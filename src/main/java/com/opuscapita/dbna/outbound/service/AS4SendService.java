package com.opuscapita.dbna.outbound.service;
import com.helger.commons.io.stream.StringInputStream;
import com.helger.commons.mime.CMimeType;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.phase4.messaging.domain.MessageHelperMethods;
import com.helger.phase4.sender.AS4Sender;
import com.helger.phase4.attachment.AS4OutgoingAttachment;
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

    // DBNA Network Configuration - injected via @Value
    @Value("${dbna.from-party-id:${spring.application.name:dbna-outbound}}")
    private String fromPartyId;
    @Value("${dbna.from-party-role:http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/initiator}")
    private String fromPartyRole;
    @Value("${dbna.to-party-role:http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/responder}")
    private String toPartyRole;
    @Value("${dbna.service-type:}")
    private String serviceType;
    @Value("${dbna.service:http://dbna.opuscapita.com/services}")
    private String defaultService;
    @Value("${dbna.action:http://dbna.opuscapita.com/sendDocument}")
    private String defaultAction;
    
    @Value("${dbna.receiver.endpoint-url:http://localhost:8080/as4}")
    private String defaultReceiverEndpointUrl;
    
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
            AS4Configuration as4Configuration) {
        this.storage = storage;
        this.ublDocumentService = ublDocumentService;
        this.as4CryptoFactory = as4CryptoFactory;
        this.as4Configuration = as4Configuration;
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
        
        // Extract metadata from ContainerMessage to build AS4SendRequest
        // Try to get endpoint URL from route/metadata, fallback to default
        String endpointUrl = defaultReceiverEndpointUrl;
        // If there's a way to get it from cm.getRoute() or metadata, use that
        // For now, use the configured default
        
        AS4SendRequest request = AS4SendRequest.builder()
            .ublDocumentContent(ublContent)
            .receiverEndpointUrl(endpointUrl)
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
     * Core AS4 sending logic - shared by both send() and sendDocument()
     */
    public AS4SendResponse sendAS4Message(AS4SendRequest request) {
        AS4SendResponse.AS4SendResponseBuilder responseBuilder = AS4SendResponse.builder()
            .timestamp(Instant.now().toEpochMilli());
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
            
            if (request.getReceiverEndpointUrl() == null || request.getReceiverEndpointUrl().trim().isEmpty()) {
                logger.warn("Receiver endpoint URL is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Receiver endpoint URL is required")
                    .build();
            }
            
            if (request.getSenderId() == null || request.getSenderId().trim().isEmpty()) {
                logger.warn("Sender ID is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Sender ID is required")
                    .build();
            }
            
            if (request.getReceiverId() == null || request.getReceiverId().trim().isEmpty()) {
                logger.warn("Receiver ID is required");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Receiver ID is required")
                    .build();
            }
            
            // Validate UBL document
            if (!ublDocumentService.validateUBLDocument(request.getUblDocumentContent())) {
                logger.warn("Invalid UBL 2.3 document format");
                return responseBuilder
                    .success(false)
                    .status("VALIDATION_FAILED")
                    .errorMessage("Invalid UBL 2.3 document format")
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
            String conversationId = request.getConversationId() != null ? 
                request.getConversationId() : messageId;
            String service = request.getService() != null ? request.getService() : defaultService;
            String action = request.getAction() != null ? request.getAction() : defaultAction;
            // Use sender/receiver IDs from request or defaults
            String fromParty = request.getSenderId() != null ? request.getSenderId() : fromPartyId;
            String toParty = request.getReceiverId();
            logger.info("Sending AS4 message to DBNA network with X.509 certificate authentication...");
            logger.debug("Message ID: {}, From: {}, To: {}, Service: {}, Action: {}", 
                messageId, fromParty, toParty, service, action);
            logger.debug("Using AS4 keystore: {}, key alias: {}", 
                as4Configuration.getKeystorePath(), as4Configuration.getKeyAlias());
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
                var builder = new AS4Sender.BuilderUserMessage()
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
                    // Service and Action
                    .action(action)
                    .service(service, serviceType)
                    // Agreement if provided
                    .agreementRef(request.getAgreementRef())
                    // Endpoint
                    .endpointURL(request.getReceiverEndpointUrl())
                    // Payload
                    .addAttachment(attachment);
                
                // Send the message with X.509 certificate signing via AS4 keystore
                builder.sendMessageAndCheckForReceipt();

                logger.info("AS4 message sent successfully to DBNA network. Message ID: {}", messageId);
                return responseBuilder
                    .success(true)
                    .messageId(messageId)
                    .status("SENT")
                    .build();
                    
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
}
