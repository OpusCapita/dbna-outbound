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
            SMPService smpService) {
        this.storage = storage;
        this.ublDocumentService = ublDocumentService;
        this.as4CryptoFactory = as4CryptoFactory;
        this.as4Configuration = as4Configuration;
        this.smlLookupService = smlLookupService;
        this.smpService = smpService;
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
        
        // Determine receiver endpoint URL
        String receiverEndpointUrl = resolveReceiverEndpointUrl(
            cm.getMetadata().getRecipientId(),
            cm.getMetadata().getDocumentTypeIdentifier(),
            cm.getMetadata().getProfileTypeIdentifier()
        );

        // Extract metadata from ContainerMessage to build AS4SendRequest
        AS4SendRequest request = AS4SendRequest.builder()
            .ublDocumentContent(ublContent)
            .receiverEndpointUrl(receiverEndpointUrl)
            .senderId(cm.getMetadata().getSenderId())
            .receiverId(cm.getMetadata().getRecipientId())
            .conversationId(cm.getMetadata().getMessageId())
            .documentType(cm.getMetadata().getDocumentTypeIdentifier())
            .processId(cm.getMetadata().getProfileTypeIdentifier())
            .signMessage(true)  // Always sign AS4 messages for DBNA
            .encryptMessage(false)  // Configure as needed
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
     * Resolves the receiver endpoint URL by checking override first, then querying SMP if needed
     *
     * @param receiverId Receiver party identifier (scheme::id)
     * @param documentTypeId Document type identifier
     * @param processId Business process identifier
     * @return The receiver endpoint URL
     * @throws Exception if endpoint resolution fails
     */
    private String resolveReceiverEndpointUrl(String receiverId, String documentTypeId, String processId) throws Exception {
        // Step 1: If override is set, use it
        if (isValidString(receiverEndpointOverride)) {
            logger.info("Using configured receiver endpoint override: {}", receiverEndpointOverride);
            return receiverEndpointOverride;
        }

        logger.info("No receiver endpoint override configured, querying SMP for endpoint");

        // Step 2: Get SMP endpoint
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

        // Step 3: Query SMP for the receiver endpoint URL
        logger.info("Querying SMP for receiver endpoint - DocumentType: {}, ProcessId: {}", documentTypeId, processId);
        String receiverEndpointUrl = smpService.discoverServiceEndpoint(activeSmpUrl, receiverId, documentTypeId, processId);

        if (!isValidString(receiverEndpointUrl)) {
            throw new IllegalStateException(
                String.format("Failed to discover receiver endpoint from SMP for documentType: %s, process: %s",
                    documentTypeId, processId));
        }

        logger.info("Successfully resolved receiver endpoint from SMP: {}", receiverEndpointUrl);
        return receiverEndpointUrl;
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
                    // Create AS4 outgoing attachment from UBL element
                    // Serialize Element to byte array for attachment
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.xml.transform.TransformerFactory.newInstance().newTransformer()
                        .transform(new javax.xml.transform.dom.DOMSource(ublElement),
                                  new javax.xml.transform.stream.StreamResult(baos));

                    AS4OutgoingAttachment attachment = AS4OutgoingAttachment.builder()
                        .data(baos.toByteArray())
                        .mimeType(CMimeType.APPLICATION_XML)
                        .charset(StandardCharsets.UTF_8)
                        .build();

                    // Build and send AS4 User Message for DBNA network using Phase4 builder
                    // Ensure global scope is set before creating the builder
                    // This is required by Phase4's MetaAS4Manager singleton
                    var builder = ensureScopeAndCreateBuilder(
                        messageId, conversationId, fromParty, toParty, request, as4CryptoFactory
                    );

                    // Add the attachment to the builder
                    builder.addAttachment(attachment);

                    // Send the message with X.509 certificate signing via AS4 keystore
                    // Important: Phase4 may log warnings about missing PMode but still attempt to send
                    logger.info("Initiating AS4 message send via Phase4");
                    logger.debug("Builder configuration: service=urn:oasis:names:tc:ebxml-msg:service, " +
                        "action=Send, from={}, to={}, endpoint={}",
                        fromParty, toParty, request.getReceiverEndpointUrl());

                    // Call sendMessageAndCheckForReceipt and capture the result
                    // This returns an enum indicating success or failure of the send operation
                    Object sendResult = null;
                    try {
                        logger.debug("Calling Phase4 sendMessageAndCheckForReceipt()...");
                        sendResult = builder.sendMessageAndCheckForReceipt();
                        logger.debug("Phase4 sendMessageAndCheckForReceipt() returned: {} (type: {})",
                            sendResult, sendResult.getClass().getSimpleName());
                    } catch (Exception e) {
                        logger.error("Phase4 sendMessageAndCheckForReceipt() threw an exception", e);

                        // Check if the exception indicates a configuration issue
                        String exMsg = e.getMessage();
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
                        String errorMsg = String.format("AS4 send failed with status: %s", resultName);
                        return responseBuilder
                            .success(false)
                            .status("FAILED")
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

        // Build the base builder with all required AS4 parameters
        // Phase4's BuilderUserMessage requires several mandatory fields to create a valid AS4 message
        var builder = new AS4Sender.BuilderUserMessage()
            .cryptoFactory(as4CryptoFactory)
            // PMode ID - CRITICAL: Phase4 requires a PMode to be set
            // Using BDXR PMode ID "bdxr-as4-1.0" registered by phase4-profile-dbnalliance
            // This PMode is automatically discovered by Phase4 at runtime
            .pmodeID(com.opuscapita.dbna.outbound.config.DBNAPModeConfiguration.getDBNAPModeId())
            // ...existing code...
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

        // Payload - will be added by the caller (in sendAS4MessageInternal)
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
}
