package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.exception.DocumentValidationException;
import com.opuscapita.dbna.outbound.exception.SMLLookupException;
import com.opuscapita.dbna.outbound.exception.SMPDiscoveryException;
import com.opuscapita.dbna.outbound.exception.AS4TransmissionException;
import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.model.SMPServiceInfo;
import com.opuscapita.dbna.outbound.service.AS4SendService;
import com.opuscapita.dbna.outbound.service.CertificateValidationService;
import com.opuscapita.dbna.outbound.service.SMLLookupService;
import com.opuscapita.dbna.outbound.service.SMPService;
import com.opuscapita.dbna.outbound.service.UBLDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.cert.X509Certificate;

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
    
    @Value("${dbna.smp.url:}")
    private String smpEndpointOverride;

    @Value("${dbna.receiver.url:}")
    private String receiverEndpointOverride;

    private final AS4SendService as4SendService;
    private final SMLLookupService smlLookupService;
    private final SMPService smpService;
    private final CertificateValidationService certificateValidationService;
    private final UBLDocumentService ublDocumentService;

    /**
     * Constructor injection for all dependencies
     * Ensures all required services are available and promotes immutability
     */
    public AS4SendController(
            AS4SendService as4SendService,
            SMLLookupService smlLookupService,
            SMPService smpService,
            CertificateValidationService certificateValidationService,
            UBLDocumentService ublDocumentService) {
        this.as4SendService = as4SendService;
        this.smlLookupService = smlLookupService;
        this.smpService = smpService;
        this.certificateValidationService = certificateValidationService;
        this.ublDocumentService = ublDocumentService;
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
        logger.info("\n======== DOCUMENT SEND REQUEST =========\n" +
            "  Sender ID:      {}\n" +
            "  Receiver ID:    {}\n" +
            "  Document Type:  {}\n" +
            "  Process ID:     {}\n" +
            "  Document Size:  {} bytes\n" +
            "========================================",

            senderId, receiverId, docTypeId, processId,
            documentContent != null ? documentContent.length() : 0);
        logger.debug("Document content preview (first 200 chars): {}",
            documentContent != null && documentContent.length() > 200 ? documentContent.substring(0, 200) + "..." : documentContent);

        // Step 1: Validate document is valid UBL 2.3 XML
        logger.info("Step 1: Validating document is valid UBL 2.3 XML");
        if (documentContent == null || documentContent.trim().isEmpty()) {
            throw new DocumentValidationException("Document content is required");
        }
        ublDocumentService.validateUBLDocument(documentContent);

        // Step 2: Parse and validate receiver identifier
        String[] receiverParts = receiverId.split("::");
        if (receiverParts.length != 2) {
            throw new DocumentValidationException("Receiver ID must be in format: scheme::identifier");
        }
        
        String receiverScheme = receiverParts[0];
        String receiverIdentifier = receiverParts[1];
        
        // Step 2: Query SML to discover receiver's SMP endpoint
        logger.info("Step 2: Querying SML for receiver's SMP endpoint - Scheme: {}, Identifier: {}",
            receiverScheme, receiverIdentifier);
        String smpEndpoint;
        // Override SMP endpoint if configured via property
        if (smpEndpointOverride != null && !smpEndpointOverride.trim().isEmpty()) {
            smpEndpoint = smpEndpointOverride;
            logger.warn("SMP endpoint override via property: {}", smpEndpoint);
        } else {
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
                throw new SMLLookupException("Failed to query SML for receiver endpoint: " + e.getMessage(), e);
            }
        }
        
        // Step 3: Query SMP to discover service endpoint and receiver certificate
        logger.info("Step 3: Querying SMP for service endpoint - DocTypeId: {}, ProcessId: {}",
            docTypeId, processId);
        SMPServiceInfo serviceInfo;

        try {
            serviceInfo = smpService.discoverServiceEndpoint(
                smpEndpoint,
                receiverId,
                docTypeId,
                processId
            );
            if (serviceInfo == null) {
                throw new SMPDiscoveryException(
                    String.format("Service endpoint not found for document type: %s, process: %s",
                        docTypeId, processId));
            }
            logger.info("SMP discovery successful - Receiver endpoint: {} (certificate available: {})",
                serviceInfo.getEndpointUrl(), serviceInfo.hasCertificateInfo());
        } catch (SMPDiscoveryException e) {
            throw e;
        } catch (Exception e) {
            throw new SMPDiscoveryException("Failed to discover service endpoint: " + e.getMessage(), e);
        }

        // Check if receiver endpoint override is configured
        if (receiverEndpointOverride != null && !receiverEndpointOverride.trim().isEmpty()) {
            logger.info("Using configured receiver endpoint override: {}", receiverEndpointOverride);
            serviceInfo.setEndpointUrl(receiverEndpointOverride);
        }
        
        String receiverEndpointUrl = serviceInfo.getEndpointUrl();
        X509Certificate receiverCertificate = serviceInfo.getReceiverCertificate();

        // Step 4: Validate receiver's certificate if available
        // Per DBNA SMP Profile v1.0:
        // - The receiver's certificate is used locally for message encryption
        // - We validate it before using it
        // - We do NOT send it to the receiver (they already have it)
        // - The receiver will validate OUR certificate (which they query from SMP)
        logger.info("Step 4: Validating receiver's X.509 certificate from SMP endpoint");
        if (receiverCertificate != null) {
            try {
                CertificateValidationService.CertificateValidationResult validationResult =
                    certificateValidationService.validateForDBNA(receiverCertificate);

                if (!validationResult.valid) {
                    throw new SMPDiscoveryException(
                        "Receiver certificate validation failed: " + validationResult.expirationError);
                }
                logger.info("Receiver certificate validated successfully - Subject: {}, will be used for local encryption",
                    receiverCertificate.getSubjectX500Principal());
            } catch (Exception e) {
                logger.warn("Failed to validate receiver certificate: {}", e.getMessage());
                // Don't fail here - continue with endpoint validation at TLS level
            }
        } else {
            logger.warn("No receiver certificate available from SMP for message encryption");
        }

         // Step 5: Build AS4SendRequest with DBNA PMode parameters
         // Per DBNA spec: We sign with our certificate, receiver will validate using our certificate from SMP
         logger.info("Step 5: Preparing AS4 message with DBNA PMode parameters");
         AS4SendRequest request = AS4SendRequest.builder()
                 .senderId(senderId)
                 .receiverId(receiverId)
                 .documentType(docTypeId)
                 .processId(processId)
                 .ublDocumentContent(documentContent)
                 .receiverEndpointUrl(receiverEndpointUrl)
                 .receiverCertificate(receiverCertificate)  // Include receiver certificate from SMP for truststore injection
                  .signMessage(true)  // PMode[1].Security - Message signing is mandatory for DBNA
                  .encryptMessage(true)  // Enable AS4-level encryption per DBNA spec - Messages are encrypted with receiver's certificate (AES-256-GCM). This is separate from HTTPS transport encryption.
                  .agreementRef("https://dbnalliance.org/agreements/access_point.html")  // PMode.Agreement
                 .build();

        // Step 6: Send document via AS4
        logger.info("Step 6: Sending UBL document via AS4 protocol to DBNA network");
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
}
