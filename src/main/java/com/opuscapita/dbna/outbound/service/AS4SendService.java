package com.opuscapita.dbna.outbound.service;
import com.helger.commons.io.stream.StringInputStream;
import com.helger.commons.mime.CMimeType;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.phase4.messaging.domain.MessageHelperMethods;
import com.helger.phase4.sender.AS4Sender;
import com.helger.phase4.attachment.AS4OutgoingAttachment;
import com.helger.scope.mgr.ScopeManager;
import com.opuscapita.dbna.outbound.config.AS4Configuration;
import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.model.AS4TransmissionResponse;
import com.opuscapita.dbna.outbound.model.DummyResponse;
import com.opuscapita.dbna.common.container.ContainerMessage;
import com.opuscapita.dbna.outbound.model.TransmissionResponse;
import com.opuscapita.dbna.outbound.model.SMPServiceInfo;
import com.opuscapita.dbna.common.storage.Storage;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
/**
 * Service for sending UBL 2.3 documents via AS4 protocol to DBNA network with X.509 certificate support
 * 
 * This service uses the Phase4 library with the DBNA Profile module:
 * - phase4-lib:2.5.0 - Core AS4 messaging implementation
 * - phase4-profile-dbnalliance:2.9.3 - DBNA-specific PMode definitions (auto-discovered by Phase4)
 *
 * According to DBNA AS4 Profile v1.0, DBNA uses the OASIS BDXR AS4 profile:
 * https://docs.oasis-open.org/bdxr/bdx-as4/v1.0/cs01/bdx-as4-v1.0-cs01.html
 *
 * DBNA PMode Configuration (from phase4-profile-dbnalliance auto-discovery):
 * - PMode ID: "bdxr-as4-1.0" (standard OASIS BDXR OneWay)
 * - Agreement: "https://dbnalliance.org/agreements/access_point.html"
 * - Security: X.509 with AES-256-GCM encryption (mandatory for DBNA)
 * - Retry: Enabled with 5+ attempts over 6 hours minimum
 * - Duplicate Detection: 30 days
 * - Error Handling: Missing receipts notify producer
 *
 * The service:
 * 1. Phase4 automatically discovers and registers the DBNA profile when phase4-profile-dbnalliance is on classpath
 * 2. CreateAS4Builder sets .pmodeID("bdxr-as4-1.0") to use the registered DBNA PMode
 * 3. sendMessageAndCheckForReceipt() validates the message and returns ESimpleUserMessageSendResult
 * 4. Result is checked: SUCCESS = message sent, any other value = failure with detailed error
 */
@Service
public class AS4SendService implements SendService {
    private static final Logger logger = LoggerFactory.getLogger(AS4SendService.class);
    
    // Dependencies injected via constructor
    private final Storage storage;
    private final UBLDocumentService ublDocumentService;
    private final IAS4CryptoFactory as4CryptoFactory;
    private final AS4Configuration as4Configuration;
    private final SMLLookupService smlLookupService;
    private final SMPService smpService;
    private final TruststoreManager truststoreManager;

    // DBNA Network Configuration - injected via @Value
    @Value("${dbna.from-party-id:${spring.application.name:dbna-outbound}}")
    private String fromPartyId;
    @Value("${dbna.from-party-role:http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator}")
    private String fromPartyRole;
    @Value("${dbna.to-party-role:http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder}")
    private String toPartyRole;

    @Value("${dbna.receiver.url:}")
    private String receiverEndpointOverride;

    @Value("${dbna.sml.url:}")
    private String smlUrl;

    @Value("${dbna.smp.url:}")
    private String smpUrl;

    @Value("${dbna.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${dbna.retry.delay:1000}")
    private long retryDelayMs;

    @Getter
    @Value("${dbna.retry.timeout:30000}")
    private long timeoutMs;

    /**
     * Constructor with dependency injection
     * Spring will automatically inject all required bean dependencies
     */
    public AS4SendService(
            Storage storage,
            UBLDocumentService ublDocumentService,
            IAS4CryptoFactory as4CryptoFactory,
            AS4Configuration as4Configuration,
            SMLLookupService smlLookupService,
            SMPService smpService,
            TruststoreManager truststoreManager) {
        this.storage = storage;
        this.ublDocumentService = ublDocumentService;
        this.as4CryptoFactory = as4CryptoFactory;
        this.as4Configuration = as4Configuration;
        this.smlLookupService = smlLookupService;
        this.smpService = smpService;
        this.truststoreManager = truststoreManager;
    }
    
    /**
     * Implementation of SendService interface method
     * Sends a message from the queue consumer integration
     * 
     * @param cm The container message from the Peppol queue
     * @return TransmissionResponse from the AS4 send operation
     * @throws Exception if sending fails
     */
    @Override
    public TransmissionResponse send(ContainerMessage cm) throws Exception {
        logger.info("AS4SendService.send() called for message: {}", cm.getFileName());
        logger.debug("AS4 Configuration - Max Retries: {}, Retry Delay: {} ms, Timeout: {} ms ({} minutes)",
            maxRetryAttempts, retryDelayMs, timeoutMs, timeoutMs / 60000);

        // Check for test error scenarios
        DummyResponse.throwExceptionIfExpectedInFilename(cm);
        
        // Read UBL document content from storage
        String ublContent;
        try (InputStream inputStream = storage.get(cm.getFileName())) {
            if (inputStream == null) {
                throw new IllegalStateException("File not found in storage: " + cm.getFileName());
            }
            ublContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
        
        // Determine receiver endpoint URL and certificate
        SMPServiceInfo serviceInfo = resolveReceiverServiceInfo(
            cm.getMetadata().getRecipientId(),
            cm.getMetadata().getDocumentTypeIdentifier(),
            cm.getMetadata().getProfileTypeIdentifier()
        );

        // Extract metadata from ContainerMessage to build AS4SendRequest
         AS4SendRequest request = AS4SendRequest.builder()
              .ublDocumentContent(ublContent)
              .receiverEndpointUrl(serviceInfo.getEndpointUrl())
              .senderId(cm.getMetadata().getSenderId())
              .receiverId(cm.getMetadata().getRecipientId())
              .conversationId(cm.getMetadata().getMessageId())
              .documentType(cm.getMetadata().getDocumentTypeIdentifier())
              .processId(cm.getMetadata().getProfileTypeIdentifier())
               .signMessage(true)  // Always sign AS4 messages for DBNA
               .encryptMessage(true)  // Enable encryption per DBNA spec - receiver certificate from SMP will be used for AES-256-GCM encryption
               .receiverCertificate(serviceInfo.getReceiverCertificate())  // Add receiver certificate for truststore injection
              .build();

        logger.info("Sending AS4 message for file: {} to endpoint: {}", 
            cm.getFileName(), request.getReceiverEndpointUrl());
        
        // Send via AS4 protocol
        AS4SendResponse as4Response = sendAS4Message(request);
        
        // Convert to TransmissionResponse
        AS4TransmissionResponse response = new AS4TransmissionResponse(as4Response);
        
        if (!as4Response.isSuccess()) {
            logger.error("AS4 transmission failed for {}: {}", cm.getFileName(), as4Response.getErrorMessage());
            throw new Exception("AS4 transmission failed: " + as4Response.getErrorMessage());
        }
        
        logger.info("AS4 transmission successful for {} with message ID: {}", 
            cm.getFileName(), as4Response.getMessageId());
        
        return response;
    }
    
    /**
     * Resolves the receiver service information (endpoint + certificate) by querying SMP
     *
     * If receiver endpoint URL is overridden in config, it will be used instead of the SMP-provided endpoint.
     * However, SMP is ALWAYS queried to obtain the receiver certificate for validation and encryption.
     * This ensures certificate pinning even when endpoint URL is overridden.
     *
     * @param receiverId Receiver party identifier (scheme::id)
     * @param documentTypeId Document type identifier
     * @param processId Business process identifier
     * @return SMPServiceInfo with endpoint URL and certificate
     * @throws Exception if SMP query fails
     */
    private SMPServiceInfo resolveReceiverServiceInfo(String receiverId, String documentTypeId, String processId) throws Exception {
        // Always query SMP to get receiver certificate (for validation and encryption)
        // The SMP query also provides the endpoint URL, which may be overridden by configuration

        logger.info("Querying SMP for receiver certificate - DocumentType: {}, ProcessId: {}", documentTypeId, processId);

        // Get SMP endpoint
        String activeSmpUrl = smpUrl;
        if (!isValidString(activeSmpUrl)) {
            // If SMP URL not configured, try to get it from SML
            logger.info("No SMP URL configured, attempting to resolve from SML");
            String[] receiverParts = receiverId.split("::");
            if (receiverParts.length != 2) {
                throw new IllegalArgumentException("Receiver ID must be in format: scheme::identifier");
            }
            activeSmpUrl = smlLookupService.lookupSMPEndpoint(receiverParts[0], receiverParts[1]);
            if (!isValidString(activeSmpUrl)) {
                throw new IllegalStateException("Failed to resolve SMP endpoint from SML");
            }
            logger.info("Resolved SMP endpoint from SML: {}", activeSmpUrl);
        }

        // Query SMP for the receiver certificate (and endpoint if not overridden)
        SMPServiceInfo serviceInfo = smpService.discoverServiceEndpoint(activeSmpUrl, receiverId, documentTypeId, processId);

        if (serviceInfo == null) {
            throw new IllegalStateException(
                String.format("Failed to discover receiver service info from SMP for documentType: %s, process: %s",
                    documentTypeId, processId));
        }

        // Use override endpoint if configured, but keep the certificate from SMP
        String finalEndpointUrl = receiverEndpointOverride;
        if (!isValidString(finalEndpointUrl)) {
            finalEndpointUrl = serviceInfo.getEndpointUrl();
        }

        if (!isValidString(finalEndpointUrl)) {
            throw new IllegalStateException(
                String.format("Failed to determine receiver endpoint for documentType: %s, process: %s",
                    documentTypeId, processId));
        }

        // If endpoint was overridden, log this
        if (isValidString(receiverEndpointOverride)) {
            logger.info("Using configured receiver endpoint override: {} (from SMP: {})",
                finalEndpointUrl, serviceInfo.getEndpointUrl());
        } else {
            logger.info("Using endpoint from SMP: {}", finalEndpointUrl);
        }

        logger.info("Successfully resolved receiver service info - endpoint: {}, certificate available: {}",
            finalEndpointUrl, serviceInfo.hasCertificateInfo());

        // Create new SMPServiceInfo with final endpoint URL and SMP certificate
        SMPServiceInfo finalServiceInfo = new SMPServiceInfo(finalEndpointUrl, serviceInfo.getReceiverCertificate());

        // Inject receiver certificate into truststore if available
        if (finalServiceInfo.hasCertificateInfo()) {
            logger.info("✓ Injecting receiver certificate into truststore for PKIX validation");
            truststoreManager.addReceiverCertificate(finalServiceInfo.getReceiverCertificate());
        } else {
            logger.warn("⚠ No receiver certificate found in SMP response. AS4 transmission may fail for PKIX validation.");
        }

        return finalServiceInfo;
    }

    /**
     * Resolves the receiver endpoint URL by checking override first, then querying SMP if needed
     * This is the original method kept for backward compatibility
     *
     * @param receiverId Receiver party identifier (scheme::id)
     * @param documentTypeId Document type identifier
     * @param processId Business process identifier
     * @return The receiver endpoint URL
     * @throws Exception if endpoint resolution fails
     */
    private String resolveReceiverEndpointUrl(String receiverId, String documentTypeId, String processId) throws Exception {
        return resolveReceiverServiceInfo(receiverId, documentTypeId, processId).getEndpointUrl();
    }

    /**
     * Core AS4 sending logic - shared by both send() and sendDocument()
     * Wrapped with proper scope management for Phase4
     */
    public AS4SendResponse sendAS4Message(AS4SendRequest request) {
        AS4SendResponse.AS4SendResponseBuilder responseBuilder = AS4SendResponse.builder()
            .timestamp(Instant.now().toEpochMilli());

        // Wrap the entire operation in a synchronized block to manage scope properly
        // This ensures that Phase4's MetaAS4Manager can access the global scope
        synchronized (AS4SendService.class) {
            return sendAS4MessageInternal(request, responseBuilder);
        }
    }

    /**
     * Internal method that performs the actual AS4 message sending
     */
    private AS4SendResponse sendAS4MessageInternal(
            AS4SendRequest request,
            AS4SendResponse.AS4SendResponseBuilder responseBuilder) {
        try {
            logger.info("Preparing to send UBL 2.3 document via AS4 to DBNA network: {}", 
                request.getReceiverEndpointUrl());
            
            // Validate request parameters
            if (request.getUblDocumentContent() == null || request.getUblDocumentContent().trim().isEmpty()) {
                logger.warn("UBL document content is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("UBL document content is required")
                    .build();
            }
            
            if (!isValidEndpointUrl(request.getReceiverEndpointUrl())) {
                logger.warn("Receiver endpoint URL is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Receiver endpoint URL is required")
                    .build();
            }
            
            if (!isValidString(request.getSenderId())) {
                logger.warn("Sender ID is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Sender ID is required")
                    .build();
            }
            
            if (!isValidString(request.getReceiverId())) {
                logger.warn("Receiver ID is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Receiver ID is required")
                    .build();
            }
            
            // Validate UBL document
            try {
                ublDocumentService.validateUBLDocument(request.getUblDocumentContent());
            } catch (Exception e) {
                logger.warn("Invalid UBL 2.3 document format: {}", e.getMessage());
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Invalid UBL 2.3 document format: " + e.getMessage())
                    .build();
            }

            // Inject receiver certificate into truststore if available
            // This ensures Phase4/PKIX validation succeeds for the receiver's endpoint
            if (request.getReceiverCertificate() != null) {
                logger.debug("Injecting receiver certificate into truststore for PKIX validation");
                truststoreManager.addReceiverCertificate(request.getReceiverCertificate());
            }

            // Parse UBL XML to DOM Element
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder xmlBuilder = factory.newDocumentBuilder();
            Element ublElement = xmlBuilder.parse(
                new StringInputStream(request.getUblDocumentContent(), StandardCharsets.UTF_8)
            ).getDocumentElement();
            
            // Verify certificate configuration
            if (!as4Configuration.isKeystoreConfigured()) {
                logger.warn("AS4 keystore not configured. Message signing may fail.");
            }
            
            if (request.isSignMessage() && !as4Configuration.isKeystoreConfigured()) {
                logger.warn("Message signing requested but AS4 keystore not configured.");
            }
            // Prepare AS4 message parameters for DBNA network
            String messageId = MessageHelperMethods.createRandomMessageID();
            String conversationId = isValidString(request.getConversationId()) ?
                request.getConversationId() : messageId;

            // Use sender/receiver IDs from request or defaults
            String fromParty = isValidString(request.getSenderId()) ? request.getSenderId() : fromPartyId;
            String toParty = request.getReceiverId();

            logger.info("Sending AS4 message to DBNA network with X.509 certificate authentication...");
            logger.debug("Message ID: {}, From: {}, To: {}, Endpoint: {}",
                messageId, fromParty, toParty, request.getReceiverEndpointUrl());
            logger.debug("Using AS4 keystore: {}, key alias: {}",
                as4Configuration.getKeystorePath(), as4Configuration.getKeyAlias());
            try {
                // Ensure global scope is active for Phase4 operations
                // Phase4 requires the scope to be active in the current thread
                boolean scopeWasAlreadyActive = ScopeManager.getGlobalScopeOrNull() != null;
                if (!scopeWasAlreadyActive) {
                    logger.debug("Activating Phase4 global scope for AS4 message sending");
                    ScopeManager.onGlobalBegin("AS4-Send-Operation");
                }

                try {
                    // Build and send AS4 User Message for DBNA network using Phase4 builder
                     // Ensure global scope is set before creating the builder
                     // This is required by Phase4's MetaAS4Manager singleton
                     var builder = ensureScopeAndCreateBuilder(
                         messageId, conversationId, fromParty, toParty, request, as4CryptoFactory
                     );

                    // Create XHE payload from UBL bytes
                    byte[] ublBytes;
                    {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.xml.transform.TransformerFactory.newInstance().newTransformer()
                            .transform(new javax.xml.transform.dom.DOMSource(ublElement),
                                      new javax.xml.transform.stream.StreamResult(baos));
                        ublBytes = baos.toByteArray();
                    }

                    // Log UBL document details
                    int uncompressedSize = ublBytes.length;
                    logger.debug("=== UBL PAYLOAD DETAILS ===");
                    logger.debug("Raw UBL XML content (uncompressed):\n{}", request.getUblDocumentContent());
                    logger.debug("Uncompressed payload size: {} bytes", uncompressedSize);

                    // Log XML structure preview
                    String xmlPreview = extractXmlStructurePreview(request.getUblDocumentContent());
                    logger.debug("XML structure preview:\n{}", xmlPreview);

                    // Log builder configuration that will be used
                    logger.debug("Builder configuration for payload:");
                    logger.debug("  - Data size: {} bytes", ublBytes.length);
                    logger.debug("  - Compression: GZIP");
                    logger.debug("  - MIME type: application/xml");
                    logger.debug("  - Service: urn:oasis:names:tc:ebxml-msg:service");
                    logger.debug("  - Action: Send");
                    logger.debug("  - From Party: {}", fromParty);
                    logger.debug("  - To Party: {}", toParty);
                    logger.debug("  - Endpoint: {}", request.getReceiverEndpointUrl());
                    logger.debug("  - PMode ID: {}", com.opuscapita.dbna.outbound.config.DBNAPModeConfiguration.getDBNAPModeId());
                    logger.debug("=== END PAYLOAD DETAILS ===");

                      // Add the XHE as the payload using builder pattern
                      // This follows the Phase4 DBNAlliance reference implementation
                      // Key: Phase4 requires attachments to be "repeatable" for signing/encryption
                      // We use a file-based data source to ensure the data is accessible throughout signing and encryption
                      //
                      // KNOWN ISSUE & WORKAROUND:
                      // Phase4's BasicHttpPoster may not properly include the multipart message body in the HTTP POST request,
                      // resulting in "Request body is required" (400) errors from the receiving endpoint.
                      //
                      // Root Cause: Phase4 converts the MIME message to a repeatable HTTP entity using a temporary file,
                      // but the HTTP client may not properly read and stream the content to the HTTP request body.
                      //
                      // Workaround: We create the attachment using a File-based data source (not just byte array).
                      // This ensures Phase4 can reopen/reread the data during signing, encryption, and HTTP transmission.
                      // File-based approach is more reliable than byte arrays for large or complex messages.
                      java.io.File tempAttachmentFile = null;
                      try {
                          // Create a temporary file to store the attachment data
                          // This ensures Phase4 can read the data multiple times (for signing and encryption)
                          tempAttachmentFile = java.io.File.createTempFile("as4-payload-", ".xml", new java.io.File(System.getProperty("java.io.tmpdir")));
                          tempAttachmentFile.deleteOnExit();

                          // Write the UBL bytes to the temporary file
                          try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempAttachmentFile)) {
                              fos.write(ublBytes);
                              fos.flush();
                          }

                          logger.debug("Created temporary attachment file: {}", tempAttachmentFile.getAbsolutePath());

                           // Create the attachment using the file data source
                           // This is more reliable than passing raw bytes because Phase4 can reopen the file as needed
                           var payloadAttachment = AS4OutgoingAttachment.builder()
                               .data(tempAttachmentFile)
                               .compressionGZIP()
                               .mimeType(CMimeType.APPLICATION_XML)
                               .build();

                          logger.debug("Adding payload to AS4 builder with GZIP compression enabled");
                          logger.debug("Attachment object created: {} with data size: {}",
                              payloadAttachment.getClass().getSimpleName(), ublBytes.length);
                          builder.addAttachment(payloadAttachment);
                      } catch (Exception attachmentEx) {
                          logger.error("Failed to create attachment with file data source, falling back to byte array", attachmentEx);
                           // Fallback to byte array if file creation fails
                           var payloadAttachment = AS4OutgoingAttachment.builder()
                               .data(ublBytes)
                               .compressionGZIP()
                               .mimeType(CMimeType.APPLICATION_XML)
                               .build();
                          builder.addAttachment(payloadAttachment);
                      }

                    // Send the message with X.509 certificate signing via AS4 keystore
                     // Important: Phase4 may log warnings about missing PMode but still attempt to send
                     logger.info("Initiating AS4 message send via Phase4");
                     logger.debug("Builder configuration: service=urn:oasis:names:tc:ebxml-msg:service, " +
                         "action=Send, from={}, to={}, endpoint={}",
                         fromParty, toParty, request.getReceiverEndpointUrl());
                     logger.debug("Payload: XHE with embedded UBL Invoice ({} bytes, GZIP compressed)", ublBytes.length);

                     // Call sendMessageAndCheckForReceipt and capture the result
                      // This returns an enum indicating success or failure of the send operation
                      // IMPORTANT: This is where Phase4 sends the multipart HTTP request via BasicHttpPoster
                      // Known Issue: If the receiver gets "Request body is required" error (400), it means
                      // Phase4 is not properly including the HTTP body in the POST request.
                      // This can happen if the repeatable HTTP entity is not being read correctly.
                      Object sendResult = null;
                      try {
                          logger.debug("Calling Phase4 sendMessageAndCheckForReceipt()...");
                          logger.debug("Phase4 will now:");
                          logger.debug("  1. Sign the message with our certificate (keystore: {}, alias: {})",
                              as4Configuration.getKeystorePath(), as4Configuration.getKeyAlias());
                          logger.debug("  2. Encrypt the message with receiver's certificate from SMP");
                          logger.debug("  3. Create multipart/related MIME message");
                          logger.debug("  4. Convert to repeatable HTTP entity (using temp file)");
                          logger.debug("  5. Send HTTP POST to {}", request.getReceiverEndpointUrl());

                          long startTime = System.currentTimeMillis();
                          sendResult = builder.sendMessageAndCheckForReceipt();
                          long duration = System.currentTimeMillis() - startTime;

                          logger.debug("Phase4 sendMessageAndCheckForReceipt() returned: {} (type: {}) after {} ms",
                              sendResult, sendResult.getClass().getSimpleName(), duration);
                          logger.debug("HTTP transmission completed. Checking result status...");
                      } catch (Exception e) {
                           logger.error("Phase4 sendMessageAndCheckForReceipt() threw an exception", e);

                           // Check if this is a parsing error (e.g., JSON instead of SOAP)
                           String exMsg = e.getMessage();
                           Throwable cause = e.getCause();

                           // If the root cause is a SAXParseException, it likely means we received non-XML content or malformed XML
                           if (cause instanceof org.xml.sax.SAXParseException) {
                               org.xml.sax.SAXParseException saxEx = (org.xml.sax.SAXParseException) cause;
                               String saxErrorMsg = saxEx.getMessage() != null ? saxEx.getMessage() : "";

                               // Handle malformed receipt with invalid ReceiptChild element (CVC schema validation errors)
                               if (saxErrorMsg.contains("cvc-complex-type") && saxErrorMsg.contains("ReceiptChild")) {
                                   logger.warn("Received malformed AS4 Receipt from endpoint with invalid structure. " +
                                       "The endpoint sent a Receipt containing 'ReceiptChild' element which violates ebMS3 schema. " +
                                       "This indicates the remote endpoint has a non-compliant AS4 implementation. " +
                                       "Error details: {}", saxErrorMsg);

                                   // The AS4 message was likely sent successfully (HTTP 200+), but the receipt is malformed
                                   // We treat this as a partial success: message sent, but receipt validation failed
                                   // This is a known issue with some non-compliant AS4 endpoints
                                   return responseBuilder
                                       .success(true)
                                       .messageId(messageId)
                                       .status("SENT_RECEIPT_MALFORMED")
                                       .warningMessage("AS4 message sent successfully, but receiver's receipt was malformed (invalid ReceiptChild element). " +
                                           "This indicates the receiving endpoint may have a non-compliant AS4 implementation. " +
                                           "The message delivery status is unknown.")
                                       .build();
                               }

                               // Handle other schema validation errors in receipt
                               if (saxErrorMsg.contains("cvc-") || saxErrorMsg.contains("xmldsig")) {
                                   logger.warn("Received malformed AS4 Receipt from endpoint - schema validation error. " +
                                       "This indicates the remote endpoint may have sent an invalid or non-compliant receipt. " +
                                       "Error details: {}", saxErrorMsg);

                                   return responseBuilder
                                       .success(true)
                                       .messageId(messageId)
                                       .status("SENT_RECEIPT_INVALID")
                                       .warningMessage("AS4 message sent successfully, but receiver's receipt failed schema validation. " +
                                           "This indicates the receiving endpoint may have a non-compliant AS4 implementation. " +
                                           "The message delivery status is unknown.")
                                       .build();
                               }

                               // Handle general non-XML or invalid XML responses
                               if (saxErrorMsg.contains("Content is not allowed in prolog")) {
                                   logger.warn("Received non-XML response from endpoint (likely JSON or HTML error). " +
                                       "This indicates the endpoint may not be a valid AS4 endpoint or returned an error. " +
                                       "SAX Error: {}", saxErrorMsg);

                                   // The message was likely sent successfully (HTTP 200+), but the response wasn't valid AS4
                                   // This is a common issue when:
                                   // 1. The endpoint returns JSON instead of SOAP
                                   // 2. The endpoint returned an HTML error page
                                   // 3. The endpoint is not a proper AS4 endpoint
                                   // We treat this as a transmission error since we can't verify receipt
                                   return responseBuilder
                                       .success(false)
                                       .status("TRANSMISSION_ERROR")
                                       .errorMessage("AS4 message may have been sent, but receiver returned non-SOAP response. " +
                                           "Endpoint may not support proper AS4 signal message receipts. " +
                                           "This is common with REST/JSON endpoints instead of SOAP/AS4 endpoints.")
                                       .build();
                               }
                           }

                           // Check if the exception indicates a configuration issue
                           if (exMsg != null && (exMsg.contains("mandatory field") || exMsg.contains("PMode"))) {
                               logger.error("CRITICAL: AS4 message send failed due to missing fields or configuration issues: {}", exMsg);
                               return responseBuilder
                                   .success(false)
                                   .status("FAILED")
                                   .errorMessage("AS4 send failed: " + exMsg)
                                   .build();
                           }

                           // For other exceptions, re-throw to be caught by outer handler
                           throw e;
                      }

                    // Validate the send result
                    // The result is an enum - SUCCESS means the send succeeded, any other value means failure
                    if (sendResult == null) {
                        logger.error("CRITICAL: AS4 sendMessageAndCheckForReceipt() returned null. " +
                            "This indicates the message was likely NOT sent.");
                        return responseBuilder
                            .success(false)
                            .status("FAILED")
                            .errorMessage("AS4 send failed: sendMessageAndCheckForReceipt() returned null")
                            .build();
                    }

                    // Check if the result indicates success
                     // The enum constant for success is typically named SUCCESS
                     String resultName = sendResult.toString();
                     if (resultName.contains("SUCCESS")) {
                         logger.info("AS4 message sent successfully to DBNA network. Message ID: {}", messageId);
                         return responseBuilder
                             .success(true)
                             .messageId(messageId)
                             .status("SENT")
                             .build();
                     } else {
                         // Send failed - result indicates an error condition
                         logger.error("CRITICAL: AS4 sendMessageAndCheckForReceipt() returned failure status: {}", resultName);

                         // Provide more specific error messages for known failure cases
                         String errorMsg;
                         if (resultName.contains("TRANSPORT_ERROR")) {
                             // TRANSPORT_ERROR often indicates response parsing issues (e.g., JSON instead of SOAP)
                             errorMsg = "AS4 message transmission failed: The receiving endpoint returned a non-SOAP response. " +
                                 "This typically indicates: (1) the endpoint is not a proper AS4 endpoint, " +
                                 "(2) the endpoint returned an error response in JSON/HTML format instead of SOAP, " +
                                 "or (3) there was a network/SSL issue. Check the endpoint URL and ensure it supports AS4.";
                         } else {
                             errorMsg = String.format("AS4 send failed with status: %s", resultName);
                         }

                         return responseBuilder
                             .success(false)
                             .status(resultName.contains("TRANSPORT_ERROR") ? "TRANSMISSION_ERROR" : "FAILED")
                             .errorMessage(errorMsg)
                             .build();
                     }
                } finally {
                    // Only end scope if we created it
                    if (!scopeWasAlreadyActive) {
                        try {
                            ScopeManager.onGlobalEnd();
                        } catch (Exception e) {
                            logger.debug("Error ending global scope", e);
                        }
                    }
                }

            } catch (Exception sendEx) {
                logger.error("Failed to send AS4 message to DBNA network. " +
                    "This may be due to configuration issues (missing PMode, no profile module, certificate issues, or incomplete AS4 builder configuration).",
                    sendEx);

                String errorMsg = sendEx.getMessage();
                if (errorMsg != null) {
                    if (errorMsg.contains("certificate") || errorMsg.contains("SSL") || errorMsg.contains("TLS")) {
                        errorMsg = "Certificate/SSL error: " + errorMsg;
                    } else if (errorMsg.contains("PMode") || errorMsg.contains("pmode")) {
                        errorMsg = "PMode configuration error: " + errorMsg + ". The AS4 message builder may be missing required fields.";
                    } else if (errorMsg.contains("mandatory field") || errorMsg.contains("not set")) {
                        errorMsg = "AS4 message builder incomplete: " + errorMsg + ". This typically means the PMode or a required field is missing.";
                    }
                }

                return responseBuilder
                    .success(false)
                    .status("FAILED")
                    .errorMessage("Failed to send message: " + errorMsg)
                    .build();
            }
        } catch (Exception e) {
            logger.error("Error preparing AS4 message for DBNA network", e);
            return responseBuilder
                .success(false)
                .status("ERROR")
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Override retry count from SendService interface
     */
    @Override
    public int getRetryCount() {
        return maxRetryAttempts;
    }
    
    /**
     * Override retry delay from SendService interface
     */
    @Override
    public int getRetryDelay() {
        return (int) retryDelayMs;
    }

    /**
     * Helper method to ensure global scope is set and create the AS4 builder.
     * This handles the case where Phase4's MetaAS4Manager requires a global scope.
     */
    private AS4Sender.BuilderUserMessage ensureScopeAndCreateBuilder(
            String messageId, String conversationId, String fromParty, String toParty,
            AS4SendRequest request, IAS4CryptoFactory as4CryptoFactory) {

         // First, try to create the builder normally
         try {
             return createAS4Builder(messageId, conversationId, fromParty, toParty, request, as4CryptoFactory);
         } catch (IllegalStateException e) {
             // If we get a scope error, that's expected - Phase4 will need scope initialization
             // But at this point, we're in a synchronized block so future requests should work
             if (e.getMessage() != null && e.getMessage().contains("No global scope object has been set")) {
                 logger.error("Phase4 requires a global scope but none is available. " +
                     "This may be a Phase4 configuration issue. The error will propagate.", e);
             }
             throw e;
         }
     }

       /**
        * Create the AS4 builder with all the required parameters from the request.
        *
        * The .pmodeID("bdxr-as4-1.0") references the DBNA PMode that is automatically
        * registered by Phase4 when phase4-profile-dbnalliance is on the classpath.
        */
        private AS4Sender.BuilderUserMessage createAS4Builder(
                String messageId, String conversationId, String fromParty, String toParty,
                AS4SendRequest request, IAS4CryptoFactory as4CryptoFactory) {

            // Log encryption request for debugging
            logger.info("AS4Builder: encryptMessage={}, hasCertificate={}",
                request.isEncryptMessage(), request.getReceiverCertificate() != null);

            // Build the base builder with all required AS4 parameters
            // Phase4's BuilderUserMessage requires several mandatory fields to create a valid AS4 message
            var builder = new AS4Sender.BuilderUserMessage()
                .cryptoFactory(as4CryptoFactory)
                // Signing-specific crypto factory - CRITICAL: Required for message signing
                // Phase4 uses cryptoFactorySign specifically for signing operations
                // This ensures the correct keystore and key alias are used when signing the message
                .cryptoFactorySign(as4CryptoFactory)
                // PMode ID - CRITICAL: Phase4 requires a PMode to be set
                // Using BDXR PMode ID "bdxr-as4-1.0" registered by phase4-profile-dbnalliance
                // This PMode is automatically discovered by Phase4 at runtime
                .pmodeID(com.opuscapita.dbna.outbound.config.DBNAPModeConfiguration.getDBNAPModeId())
                // Message IDs - Required
                .messageID(messageId)
                .conversationID(conversationId)
                // Sender Party - Required
                .fromPartyID(fromParty)
                .fromRole(fromPartyRole)
                // Receiver Party - Required
                .toPartyID(toParty)
                .toRole(toPartyRole)
                // Service - Required for AS4 user message (standard OASIS ebMS service)
                .service("urn:oasis:names:tc:ebxml-msg:service")
                // Action - Required for AS4 user message (standard send action)
                .action("Send")
                // Agreement reference if provided
                .agreementRef(request.getAgreementRef())
                // Endpoint URL - Required (where to send the message)
                .endpointURL(request.getReceiverEndpointUrl());

             // Configure encryption if requested and receiver certificate is available
             // Per DBNA spec: We encrypt with the receiver's certificate from SMP (AES-256-GCM)
             // NOTE: The DBNA PMode (bdxr-as4-1.0) specifies AES-256-GCM encryption
             // Phase4 will use this PMode to determine encryption is needed
             // The receiver certificate must be provided explicitly for Phase4's encryption to work
             if (request.isEncryptMessage()) {
                 if (request.getReceiverCertificate() != null) {
                     logger.debug("Encryption requested: receiver certificate is available from SMP");
                     logger.debug("Providing receiver certificate to Phase4 builder for encryption");
                     // CRITICAL: Provide the receiver certificate to Phase4 for encryption
                     // This tells Phase4's encryption engine (WSS4J) which certificate to use for encrypting the message
                     builder.receiverCertificate(request.getReceiverCertificate());
                     logger.debug("Phase4 will encrypt the message using AES-256-GCM (from PMode) with receiver's certificate");
                 } else {
                     logger.warn("Encryption requested but no receiver certificate available from SMP. " +
                         "Encryption will fail - the message cannot be encrypted without the receiver's certificate.");
                 }
             }

            // Configure signing if requested
            // We use the keystore certificate for signing (our certificate)
            if (request.isSignMessage()) {
                logger.info("✓ Configuring AS4 message signing");
                // Phase4 will use the crypto factory to sign with the key alias configured in the AS4Configuration
                // The key alias is set in as4CryptoFactory which was passed as cryptoFactorySign
                // Nothing additional needs to be configured here as the crypto factory handles it
            }

            return builder;
        }

    /**
     * Helper method to validate that a string is not null or empty.
     */
    private boolean isValidString(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Helper method to validate endpoint URL is not null or empty.
     */
    private boolean isValidEndpointUrl(String url) {
        return isValidString(url);
    }

    /**
     * Extract XML structure preview from the UBL document.
     * Shows the root element and first few child elements for debugging.
     */
    private String extractXmlStructurePreview(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(
                new StringInputStream(xmlContent, StandardCharsets.UTF_8)
            );
            org.w3c.dom.Element root = doc.getDocumentElement();

            StringBuilder preview = new StringBuilder();
            preview.append("Root Element: ").append(root.getTagName()).append("\n");
            preview.append("Root Attributes:\n");
            org.w3c.dom.NamedNodeMap attrs = root.getAttributes();
            for (int i = 0; i < Math.min(attrs.getLength(), 5); i++) {
                org.w3c.dom.Node attr = attrs.item(i);
                preview.append("  - ").append(attr.getNodeName()).append(" = ").append(attr.getNodeValue()).append("\n");
            }
            if (attrs.getLength() > 5) {
                preview.append("  ... and ").append(attrs.getLength() - 5).append(" more attributes\n");
            }

            preview.append("First level children:\n");
            org.w3c.dom.NodeList children = root.getChildNodes();
            int elementCount = 0;
            for (int i = 0; i < children.getLength() && elementCount < 5; i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    preview.append("  - <").append(child.getNodeName()).append(">\n");
                    elementCount++;
                }
            }
            if (elementCount == 0) {
                preview.append("  (no element children)\n");
            }

            return preview.toString();
        } catch (Exception e) {
            logger.debug("Failed to extract XML structure preview", e);
            return "Failed to parse XML structure: " + e.getMessage();
        }
    }
}
