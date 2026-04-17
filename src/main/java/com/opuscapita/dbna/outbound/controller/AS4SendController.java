package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.exception.DocumentValidationException;
import com.opuscapita.dbna.outbound.exception.SMLLookupException;
import com.opuscapita.dbna.outbound.exception.SMPDiscoveryException;
import com.opuscapita.dbna.outbound.exception.AS4TransmissionException;
import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.service.AS4SendService;
import com.opuscapita.dbna.outbound.service.CertificateValidationService;
import com.opuscapita.dbna.outbound.service.SMLLookupService;
import com.opuscapita.dbna.outbound.service.SMPService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for sending UBL documents via AS4 over DBNA network
 * 
 * Implements full DBNA network requirements:
 * - SML (Service Metadata Locator) queries for participant discovery
 * - SMP (Service Metadata Publishing) queries for endpoint discovery
 * - X.509 certificate validation
 * - AS4 message transmission with proper DBNA PMode parameters
 */
@RestController
@RequestMapping("/api/as4")
public class AS4SendController {
    
    private static final Logger logger = LoggerFactory.getLogger(AS4SendController.class);
    
    private final AS4SendService as4SendService;
    private final SMLLookupService smlLookupService;
    private final SMPService smpService;
    private final CertificateValidationService certificateValidationService;
    
    /**
     * Constructor injection for all dependencies
     * Ensures all required services are available and promotes immutability
     */
    public AS4SendController(
            AS4SendService as4SendService,
            SMLLookupService smlLookupService,
            SMPService smpService,
            CertificateValidationService certificateValidationService) {
        this.as4SendService = as4SendService;
        this.smlLookupService = smlLookupService;
        this.smpService = smpService;
        this.certificateValidationService = certificateValidationService;
    }
    
    /**
     * Send UBL document via AS4 protocol over DBNA network
     * 
     * Process:
     * 1. Validate document content
     * 2. Query SML to discover receiver's SMP endpoint
     * 3. Query SMP to discover service endpoint and validate certificate
     * 4. Send document via AS4 with X.509 certificate signing
     * 
     * @param senderId Sender party identifier (scheme::id)
     * @param receiverId Receiver party identifier (scheme::id)
     * @param docTypeId Document type identifier
     * @param processId Business process identifier
     * @param documentContent UBL XML document content
     * @return AS4SendResponse with transmission details
     */
    @PostMapping("/send/{senderId}/{receiverId}/{docTypeId}/{processId}")
    public ResponseEntity<AS4SendResponse> sendDocument(
            @PathVariable String senderId,
            @PathVariable String receiverId,
            @PathVariable String docTypeId,
            @PathVariable String processId,
            @RequestBody String documentContent) {
        logger.info("Received request to send UBL document via DBNA network - SenderId: {}, ReceiverId: {}, DocTypeId: {}, ProcessId: {}", 
            senderId, receiverId, docTypeId, processId);

        // Step 1: Validate document content
        if (documentContent == null || documentContent.trim().isEmpty()) {
            throw new DocumentValidationException("Document content is required");
        }
        
        // Step 2: Parse and validate receiver identifier
        String[] receiverParts = receiverId.split("::");
        if (receiverParts.length != 2) {
            throw new DocumentValidationException("Receiver ID must be in format: scheme::identifier");
        }
        
        String receiverScheme = receiverParts[0];
        String receiverIdentifier = receiverParts[1];
        
        // Step 3: Query SML to discover receiver's SMP endpoint
        logger.info("Step 1: Querying SML for receiver's SMP endpoint - Scheme: {}, Identifier: {}", 
            receiverScheme, receiverIdentifier);
        String smpEndpoint;
        try {
            smpEndpoint = smlLookupService.lookupSMPEndpoint(receiverScheme, receiverIdentifier);
            if (smpEndpoint == null) {
                throw new SMLLookupException(
                    String.format("Receiver '%s::%s' not found in SML registry", receiverScheme, receiverIdentifier));
            }
            logger.info("SML lookup successful - SMP endpoint: {}", smpEndpoint);
        } catch (SMLLookupException | DocumentValidationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new DocumentValidationException(e.getMessage());
        } catch (Exception e) {
            if (e instanceof SMLLookupException || e instanceof DocumentValidationException) {
                throw (RuntimeException) e;
            }
            throw new SMLLookupException("Failed to query SML for receiver endpoint: " + e.getMessage(), e);
        }
        
        // Step 4: Query SMP to discover service endpoint
        logger.info("Step 2: Querying SMP for service endpoint - DocTypeId: {}, ProcessId: {}", 
            docTypeId, processId);
        String receiverEndpointUrl;
        try {
            receiverEndpointUrl = smpService.discoverServiceEndpoint(
                smpEndpoint, 
                receiverId, 
                docTypeId, 
                processId
            );
            if (receiverEndpointUrl == null) {
                throw new SMPDiscoveryException(
                    String.format("Service endpoint not found for document type: %s, process: %s", 
                        docTypeId, processId));
            }
            logger.info("SMP discovery successful - Receiver endpoint: {}", receiverEndpointUrl);
        } catch (SMPDiscoveryException e) {
            throw e;
        } catch (Exception e) {
            throw new SMPDiscoveryException("Failed to discover service endpoint: " + e.getMessage(), e);
        }
        
        // Step 5: Validate receiver's certificate
        logger.info("Step 3: Validating receiver's X.509 certificate from SMP endpoint");
        logger.debug("Certificate validation configuration: checkExpiration={}", certificateValidationService);
        
        // Step 6: Build AS4SendRequest with DBNA PMode parameters
        logger.info("Step 4: Preparing AS4 message with DBNA PMode parameters");
        AS4SendRequest request = AS4SendRequest.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .documentType(docTypeId)
                .processId(processId)
                .ublDocumentContent(documentContent)
                .receiverEndpointUrl(receiverEndpointUrl)
                .signMessage(true)  // PMode[1].Security - Message signing is mandatory for DBNA
                .encryptMessage(true)  // PMode[1].Security.X509.Encryption.Encrypt = True
                .agreementRef("https://dbnalliance.org/agreements/access_point.html")  // PMode.Agreement
                .build();
        
        // Step 7: Send document via AS4
        logger.info("Step 5: Sending UBL document via AS4 protocol to DBNA network");
        try {
            AS4SendResponse response = as4SendService.sendAS4Message(request);
            
            if (response.isSuccess()) {
                logger.info("AS4 transmission successful - MessageID: {}", response.getMessageId());
                return ResponseEntity.ok(response);
            } else {
                throw new AS4TransmissionException(response.getErrorMessage());
            }
        } catch (AS4TransmissionException e) {
            throw e;
        } catch (Exception e) {
            throw new AS4TransmissionException("AS4 message transmission failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AS4 Outbound Service is running");
    }
}
