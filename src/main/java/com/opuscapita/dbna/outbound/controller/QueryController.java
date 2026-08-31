package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.exception.SMLLookupException;
import com.opuscapita.dbna.outbound.exception.SMPDiscoveryException;
import com.opuscapita.dbna.outbound.model.SMPServiceInfo;
import com.opuscapita.dbna.outbound.service.SMLLookupService;
import com.opuscapita.dbna.outbound.service.SMPService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for querying DBNA SML and SMP services
 * <p>
 * Provides endpoints to:
 * - Query SML for SMP discovery
 * - Query SMP for service endpoint and certificate information
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final Logger logger = LoggerFactory.getLogger(QueryController.class);

    private final SMLLookupService smlLookupService;
    private final SMPService smpService;

    /**
     * Constructor injection for SML and SMP services
     */
    public QueryController(
            SMLLookupService smlLookupService,
            SMPService smpService) {
        this.smlLookupService = smlLookupService;
        this.smpService = smpService;
    }

    /**
     * Query SML for the SMP endpoint of a participant
     * <p>
     * Requires participant ID in format: "scheme::identifier"
     *
     * @param participantId Full participant ID in format "scheme::identifier" (required)
     * @return Map containing the SMP endpoint URL
     */
    @GetMapping("/sml")
    public ResponseEntity<Map<String, Object>> querySML(
            @RequestParam("participantId") String participantId) {

        logger.info("Received SML query - participantId: {}", participantId);

        try {
            // Parse participantId format (scheme::identifier)
            String[] parts = participantId.split("::", 2);
            if (parts.length != 2) {
                logger.warn("Invalid participantId format: {}. Expected format: scheme::identifier", participantId);
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid participantId format",
                        "message", "Expected format: scheme::identifier",
                        "example", "GLN::9999999999999"
                ));
            }

            String lookupScheme = parts[0];
            String lookupIdentifier = parts[1];

            logger.info("Looking up SMP endpoint for scheme: {}, identifier: {}", lookupScheme, lookupIdentifier);

            // Query SML
            String smpEndpoint = smlLookupService.lookupSMPEndpoint(lookupScheme, lookupIdentifier);

            if (smpEndpoint == null) {
                logger.warn("SMP endpoint not found for {}::{}", lookupScheme, lookupIdentifier);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Participant not found",
                        "message", String.format("Participant '%s::%s' not found in DBNA SML registry", lookupScheme, lookupIdentifier),
                        "scheme", lookupScheme,
                        "identifier", lookupIdentifier
                ));
            }

            logger.info("Successfully resolved SMP endpoint: {}", smpEndpoint);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("scheme", lookupScheme);
            response.put("identifier", lookupIdentifier);
            response.put("smpEndpoint", smpEndpoint);
            response.put("queryType", "DNS NAPTR");
            response.put("note", "SML query uses DNS NAPTR records to discover SMP endpoints");

            return ResponseEntity.ok(response);

        } catch (SMLLookupException e) {
            logger.error("SML lookup failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "SML lookup failed",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid argument",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Unexpected error during SML query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal server error",
                    "message", "An unexpected error occurred: " + e.getMessage()
            ));
        }
    }

    /**
     * Query SMP for service endpoints and certificate information
     * <p>
     * Accepts participant ID in either format:
     * - Full format: "scheme::identifier" as a query parameter
     * - Split format: separate "scheme" and "participantId" query parameters
     *
     * @param smpEndpoint The SMP endpoint URL
     * @param participantId Full participant ID (scheme::identifier) - optional if scheme and participantId are provided separately
     * @param scheme Participant identifier scheme (e.g., "GLN", "0192") - required if participantId is not provided
     * @param identifier Participant identifier - required if participantId is not provided
     * @param documentTypeId Document type identifier (required)
     * @param processId Business process identifier (required)
     * @return Map containing service endpoint URL and certificate information
     */
    @GetMapping("/smp")
    public ResponseEntity<Map<String, Object>> querySMP(
            @RequestParam(value = "smpEndpoint") String smpEndpoint,
            @RequestParam(value = "participantId", required = false) String participantId,
            @RequestParam(value = "scheme", required = false) String scheme,
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam(value = "documentTypeId") String documentTypeId,
            @RequestParam(value = "processId") String processId) {

        logger.info("Received SMP query - smpEndpoint: {}, participantId: {}, scheme: {}, identifier: {}, documentTypeId: {}, processId: {}",
                smpEndpoint, participantId, scheme, identifier, documentTypeId, processId);

        try {
            // Validate required parameters
            if (smpEndpoint == null || smpEndpoint.trim().isEmpty()) {
                logger.warn("Missing required parameter: smpEndpoint");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing required parameter",
                        "message", "Parameter 'smpEndpoint' is required"
                ));
            }

            if (documentTypeId == null || documentTypeId.trim().isEmpty()) {
                logger.warn("Missing required parameter: documentTypeId");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing required parameter",
                        "message", "Parameter 'documentTypeId' is required"
                ));
            }

            if (processId == null || processId.trim().isEmpty()) {
                logger.warn("Missing required parameter: processId");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing required parameter",
                        "message", "Parameter 'processId' is required"
                ));
            }

            String lookupScheme;
            String lookupIdentifier;

            // Resolve scheme and identifier from parameters
            if (participantId != null && !participantId.trim().isEmpty()) {
                // Parse full participantId format (scheme::identifier)
                String[] parts = participantId.split("::", 2);
                if (parts.length != 2) {
                    logger.warn("Invalid participantId format: {}. Expected format: scheme::identifier", participantId);
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Invalid participantId format",
                            "message", "Expected format: scheme::identifier",
                            "example", "GLN::9999999999999"
                    ));
                }
                lookupScheme = parts[0];
                lookupIdentifier = parts[1];
            } else if (scheme != null && !scheme.trim().isEmpty() && identifier != null && !identifier.trim().isEmpty()) {
                // Use separate scheme and identifier parameters
                lookupScheme = scheme;
                lookupIdentifier = identifier;
            } else {
                logger.warn("Missing required parameters for participant lookup");
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing required parameters",
                        "message", "Provide either 'participantId' (scheme::identifier) or both 'scheme' and 'identifier'",
                        "examples", new Object[]{
                                "?participantId=GLN::9999999999999&documentTypeId=...&processId=...",
                                "?scheme=GLN&identifier=9999999999999&documentTypeId=...&processId=..."
                        }
                ));
            }

            String fullParticipantId = lookupScheme + "::" + lookupIdentifier;

            logger.info("Querying SMP endpoint: {} for participant: {}, documentType: {}, process: {}",
                    smpEndpoint, fullParticipantId, documentTypeId, processId);

            // Query SMP for service information
            SMPServiceInfo serviceInfo = smpService.discoverServiceEndpoint(
                    smpEndpoint,
                    fullParticipantId,
                    documentTypeId,
                    processId
            );

            if (serviceInfo == null) {
                logger.warn("Service endpoint not found in SMP for participant: {}, documentType: {}, process: {}",
                        fullParticipantId, documentTypeId, processId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Service endpoint not found",
                        "message", String.format("No service endpoint found for document type '%s' and process '%s'", documentTypeId, processId),
                        "scheme", lookupScheme,
                        "identifier", lookupIdentifier,
                        "documentTypeId", documentTypeId,
                        "processId", processId
                ));
            }

            logger.info("Successfully discovered service endpoint: {}", serviceInfo.getEndpointUrl());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("scheme", lookupScheme);
            response.put("identifier", lookupIdentifier);
            response.put("documentTypeId", documentTypeId);
            response.put("processId", processId);
            response.put("endpointUrl", serviceInfo.getEndpointUrl());
            response.put("serviceReference", serviceInfo.getServiceReference());

            // Add certificate information if available
            if (serviceInfo.hasCertificateInfo()) {
                X509Certificate cert = serviceInfo.getReceiverCertificate();
                Map<String, String> certInfo = new HashMap<>();
                certInfo.put("subjectDN", cert.getSubjectX500Principal().toString());
                certInfo.put("issuerDN", cert.getIssuerX500Principal().toString());
                certInfo.put("serialNumber", cert.getSerialNumber().toString());
                certInfo.put("notBefore", cert.getNotBefore().toString());
                certInfo.put("notAfter", cert.getNotAfter().toString());
                response.put("certificate", certInfo);
            } else {
                response.put("certificate", null);
                response.put("certificateAvailable", false);
            }

            // Add raw XML responses
            if (serviceInfo.getServiceGroupXml() != null) {
                response.put("serviceGroupXml", serviceInfo.getServiceGroupXml());
            }
            if (serviceInfo.getServiceMetadataXml() != null) {
                response.put("serviceMetadataXml", serviceInfo.getServiceMetadataXml());
            }

            return ResponseEntity.ok(response);

        } catch (SMLLookupException e) {
            logger.error("SML lookup failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "SML lookup failed",
                    "message", e.getMessage()
            ));
        } catch (SMPDiscoveryException e) {
            logger.error("SMP discovery failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "SMP discovery failed",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid argument",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Unexpected error during SMP query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal server error",
                    "message", "An unexpected error occurred: " + e.getMessage()
            ));
        }
    }
}




