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
 * This service uses the Phase4 library (com.helger.phase4:phase4-lib) which provides generic AS4 messaging
 * capabilities. The Phase4 library is configured specifically for the DBNA (Digital Business Networks Alliance)
 * network through:
 * - DBNA-specific party roles (initiator/responder)
 * - DBNA service and action endpoints
 * - X.509 certificate-based authentication
 * - UBL 2.3 document support
 * 
 * Note: There is no separate "phase4-dbnalliance-client" artifact. The phase4-lib provides the necessary
 * AS4 messaging functionality, and DBNA-specific configuration is applied through the AS4Sender builder
 * pattern with DBNA network parameters.
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

    @Value("${dbna.retry.delay-ms:1000}")
    private long retryDelayMs;
    
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
                    builder.sendMessageAndCheckForReceipt();

                    logger.info("AS4 message sent successfully to DBNA network. Message ID: {}", messageId);
                    return responseBuilder
                        .success(true)
                        .messageId(messageId)
                        .status("SENT")
                        .build();
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
                logger.error("Failed to send AS4 message to DBNA network. This may be due to certificate issues.", sendEx);
                String errorMsg = sendEx.getMessage();
                if (errorMsg != null && (errorMsg.contains("certificate") || errorMsg.contains("SSL") || errorMsg.contains("TLS"))) {
                    errorMsg = "Certificate/SSL error: " + errorMsg;
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
     */
    private AS4Sender.BuilderUserMessage createAS4Builder(
            String messageId, String conversationId, String fromParty, String toParty,
            AS4SendRequest request, IAS4CryptoFactory as4CryptoFactory) {

        return new AS4Sender.BuilderUserMessage()
            .cryptoFactory(as4CryptoFactory)
            // Message IDs
            .messageID(messageId)
            .conversationID(conversationId)
            // Sender Party
            .fromPartyID(fromParty)
            .fromRole(fromPartyRole)
            // Receiver Party
            .toPartyID(toParty)
            .toRole(toPartyRole)
            // Agreement if provided
            .agreementRef(request.getAgreementRef())
            // Endpoint from request
            .endpointURL(request.getReceiverEndpointUrl())
            // Payload - note: attachment was created in the calling method
            // We'll need to add it in the calling method
            ;
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
