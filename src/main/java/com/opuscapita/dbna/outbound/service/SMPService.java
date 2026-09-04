package com.opuscapita.dbna.outbound.service;

import com.opuscapita.dbna.outbound.exception.SMPDiscoveryException;
import com.opuscapita.dbna.outbound.model.SMPServiceInfo;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for querying DBNA Service Metadata Publishing (SMP) to discover service endpoints
 * 
 * According to DBNA SMP Profile v1.0:
 * - Queries ServiceGroup resources to discover supported document types and processes
 * - Queries ServiceMetadata resources to get endpoint information
 * - Implements caching with "If-Modified-Since" headers
 * - Uses HTTPS with proper TLS/SSL certificates
 */
@Service
public class SMPService {
    private static final Logger logger = LoggerFactory.getLogger(SMPService.class);
    
    private static final String HEADER_IF_MODIFIED_SINCE = "If-Modified-Since";
    private static final String HEADER_LAST_MODIFIED = "Last-Modified";
    private static final long CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final HttpClient httpClient;
    
    // Cache for ServiceGroup and ServiceMetadata resources
    private final ConcurrentHashMap<String, CachedSMPResource> resourceCache = new ConcurrentHashMap<>();
    
    @Autowired
    public SMPService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * Discovers service endpoints for a given document type and process
     * According to DBNA SMP Profile v1.0, also extracts the receiver's certificate
     * for certificate pinning validation.
     *
     * @param smpEndpoint The base URL of the SMP service
     * @param participantId The participant identifier (scheme::id)
     * @param documentTypeId The document type identifier
     * @param processId The process identifier
     * @return SMPServiceInfo with endpoint URL and certificate (if available), or null if not found
     * @throws Exception if service discovery fails
     */
    public SMPServiceInfo discoverServiceEndpoint(String smpEndpoint, String participantId,
                                          String documentTypeId, String processId) throws Exception {
        if (smpEndpoint == null || smpEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("SMP endpoint is required");
        }

        // Ensure SMP endpoint has a proper scheme (DBNA requires HTTPS)
        smpEndpoint = ensureUrlScheme(smpEndpoint);

        logger.info("Discovering service endpoint from SMP: {}", smpEndpoint);
        logger.debug("Participant: {}, DocumentType: {}, Process: {}", participantId, documentTypeId, processId);
        
         try {
             // Step 1: Query ServiceGroup to acquire the exact serviceReference for the requested document type
             String serviceGroupUrl = buildServiceGroupUrl(smpEndpoint, participantId);
             logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);

            String serviceGroupXml = null;
            String serviceReference = null;
            try {
                serviceGroupXml = executeHttpGet(serviceGroupUrl);
                logger.debug("ServiceGroup resource retrieved successfully");
                serviceReference = extractServiceReferenceFromServiceGroup(serviceGroupXml, documentTypeId);
            } catch (SMPDiscoveryException e) {
                logger.warn("Failed to retrieve ServiceGroup resource: HTTP {} from SMP", e.getSmpHttpStatusCode());
                throw e;
            } catch (Exception e) {
                logger.warn("Failed to retrieve ServiceGroup resource: {}", e.getMessage());
            }

            if (serviceReference == null) {
                logger.warn("Document type {} not supported by participant {}", documentTypeId, participantId);
                return null;
            }
            logger.debug("Acquired serviceReference from ServiceGroup: {}", serviceReference);

            // Step 2: Query ServiceMetadata using the serviceReference to get endpoint information and certificate
            String serviceMetadataXml = queryServiceMetadataXML(smpEndpoint, participantId, serviceReference, processId);
            if (serviceMetadataXml == null) {
                logger.warn("No service metadata found for serviceReference: {}, process: {}", serviceReference, processId);
                return null;
            }

            String endpoint = extractEndpointFromXML(serviceMetadataXml);
            X509Certificate receiverCert = extractCertificateFromXML(serviceMetadataXml);

            if (endpoint != null) {
                SMPServiceInfo serviceInfo = new SMPServiceInfo(endpoint, receiverCert, serviceReference);
                serviceInfo.setServiceGroupXml(serviceGroupXml);
                serviceInfo.setServiceMetadataXml(serviceMetadataXml);
                logger.info("Successfully discovered service endpoint: {} with certificate available: {}",
                    endpoint, (receiverCert != null));
                return serviceInfo;
            } else {
                logger.warn("No service endpoint found for serviceReference: {}, process: {}", serviceReference, processId);
                return null;
            }
        } catch (SMPDiscoveryException e) {
            logger.error("SMP service discovery failed with HTTP error: {}", e.getSmpHttpStatusCode(), e);
            throw new Exception("SMP service discovery failed (HTTP " + e.getSmpHttpStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error discovering service endpoint from SMP", e);
            throw new Exception("SMP service discovery failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Queries the ServiceGroup resource to acquire the exact serviceReference for the requested document type
     *
     * Returns the serviceReference (document type ID) as published in the SMP, which ensures proper
     * encoding of special characters like ## that might be present in the document type identifier.
     */
     private String getServiceReferenceFromServiceGroup(String smpEndpoint, String participantId, String documentTypeId) {
         String serviceGroupUrl = buildServiceGroupUrl(smpEndpoint, participantId);

         logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);

        try {
            String response = executeHttpGet(serviceGroupUrl);
            logger.debug("ServiceGroup resource retrieved successfully");

            // Parse XML and acquire the exact serviceReference for the document type
            return extractServiceReferenceFromServiceGroup(response, documentTypeId);
        } catch (SMPDiscoveryException e) {
            logger.warn("Failed to retrieve ServiceGroup resource: HTTP {} from SMP", e.getSmpHttpStatusCode());
            return null;
        } catch (Exception e) {
            logger.warn("Failed to retrieve ServiceGroup resource: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a document type is supported by querying the ServiceGroup resource
     */
     @Deprecated(forRemoval = true)
     private boolean isDocumentTypeSupported(String smpEndpoint, String participantId, String documentTypeId) {
         String serviceGroupUrl = buildServiceGroupUrl(smpEndpoint, participantId);

         logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);

        try {
            String response = executeHttpGet(serviceGroupUrl);
            logger.debug("ServiceGroup resource retrieved successfully");

            // Parse XML and check if document type is supported
            return isDocumentTypeSupportedInServiceGroup(response, documentTypeId);
        } catch (SMPDiscoveryException e) {
            logger.warn("Failed to retrieve ServiceGroup resource: HTTP {} from SMP", e.getSmpHttpStatusCode());
            return false;
        } catch (Exception e) {
            logger.warn("Failed to retrieve ServiceGroup resource: {}", e.getMessage());
            return false;
        }
    }
    
     /**
      * Queries the ServiceMetadata resource to get the XML response for a specific serviceReference and process
      * The XML contains both endpoint URL and certificate information
      */
     private String queryServiceMetadataXML(String smpEndpoint, String participantId, String serviceReference, String processId) throws SMPDiscoveryException {
         String serviceMetadataUrl = buildServiceMetadataUrl(smpEndpoint, participantId, serviceReference);

         logger.debug("Querying ServiceMetadata resource: {}", serviceMetadataUrl);

         try {
             String response = executeHttpGet(serviceMetadataUrl);
             logger.debug("ServiceMetadata resource retrieved successfully");
             return response;
         } catch (SMPDiscoveryException e) {
             logger.warn("Failed to retrieve ServiceMetadata resource: HTTP {} from SMP", e.getSmpHttpStatusCode());
             throw e;
         } catch (IOException e) {
             logger.warn("Failed to retrieve ServiceMetadata resource: {}", e.getMessage());
             return null;
         }
     }

     /**
      * Queries the ServiceMetadata resource to get the endpoint for a specific serviceReference and process
      */
    private String queryServiceEndpoint(String smpEndpoint, String participantId, String serviceReference, String processId) throws SMPDiscoveryException {
         String serviceMetadataUrl = buildServiceMetadataUrl(smpEndpoint, participantId, serviceReference);

         logger.debug("Querying ServiceMetadata resource: {}", serviceMetadataUrl);

         try {
             String response = executeHttpGet(serviceMetadataUrl);
             logger.debug("ServiceMetadata resource retrieved successfully");

             // Parse endpoint from response XML
             return extractEndpointFromXML(response);
         } catch (SMPDiscoveryException e) {
             logger.warn("Failed to retrieve ServiceMetadata resource: HTTP {} from SMP", e.getSmpHttpStatusCode());
             throw e;
         } catch (IOException e) {
             logger.warn("Failed to retrieve ServiceMetadata resource: {}", e.getMessage());
             return null;
         }
     }

    /**
     * Executes an HTTP GET request with caching support
     *
     * @param url The URL to query
     * @return The response content
     * @throws SMPDiscoveryException if a valid HTTP error response is received from SMP (status code not 200 or 304)
     * @throws IOException if there's a network error or other I/O exception
     */
    private String executeHttpGet(String url) throws IOException, SMPDiscoveryException {
        CachedSMPResource cached = resourceCache.get(url);
        
        HttpGet httpGet = new HttpGet(url);
        try {
            // Add If-Modified-Since header if we have a cached version
            if (cached != null && cached.lastModified != null) {
                httpGet.setHeader(HEADER_IF_MODIFIED_SINCE, cached.lastModified);
                logger.debug("Using cached resource with If-Modified-Since: {}", cached.lastModified);
            }
            
            var response = httpClient.execute(httpGet, httpResponse -> {
                int statusCode = httpResponse.getCode();
                
                if (statusCode == 304) {
                    logger.debug("Resource not modified, using cached version");
                    if (cached != null) {
                        return cached.content;
                    }
                }
                
                if (statusCode == 200) {
                    String content = EntityUtils.toString(httpResponse.getEntity());
                    String lastModified = null;
                    
                    var lastModifiedHeader = httpResponse.getFirstHeader(HEADER_LAST_MODIFIED);
                    if (lastModifiedHeader != null) {
                        lastModified = lastModifiedHeader.getValue();
                    }
                    
                    // Cache the resource
                    resourceCache.put(url, new CachedSMPResource(content, lastModified, Instant.now().toEpochMilli()));
                    logger.debug("Cached SMP resource with Last-Modified: {}", lastModified);
                    
                    return content;
                }
                
                // Valid HTTP error response from SMP - throw SMPDiscoveryException
                logger.warn("SMP returned HTTP error response: {} for URL: {}", statusCode, url);
                throw new SMPDiscoveryException(
                    "SMP service returned HTTP " + statusCode + " error",
                    statusCode,
                    url
                );
            });
            
            return response;
        } finally {
            httpGet.reset();
        }
    }
    
     /**
      * Extracts the receiver's X.509 certificate from SMP ServiceMetadata XML response
      * According to DBNA SMP Profile v1.0, the Certificate element within Endpoint contains the receiver's certificate
      * which is used by us (the sender) for:
      * 1. Validating the receiver's endpoint legitimacy
      * 2. Encrypting the AS4 message
      *
      * The receiver will NOT use this certificate - they will use OUR certificate (obtained from SMP)
      * to validate our message signature.
      *
      * XML Structure example:
      * <sma:Endpoint>
      *   <smb:AddressURI>https://example.com/as4</smb:AddressURI>
      *   <sma:Certificate>
      *     <smb:TypeCode>bdxr-as4-signing-encryption</smb:TypeCode>
      *     <smb:ActivationDate>2021-09-01Z</smb:ActivationDate>
      *     <smb:ExpirationDate>2023-08-31Z</smb:ExpirationDate>
      *     <smb:ContentBinaryObject mimeCode="application/base64">BASE64ENCODEDCERT</smb:ContentBinaryObject>
      *   </sma:Certificate>
      * </sma:Endpoint>
      *
      * @param xml The ServiceMetadata XML response
      * @return The X509Certificate if found, null otherwise
      */
     private X509Certificate extractCertificateFromXML(String xml) {
         if (xml == null) return null;

         try {
             DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
             factory.setNamespaceAware(true);
             DocumentBuilder builder = factory.newDocumentBuilder();

             Document doc = builder.parse(new InputSource(new StringReader(xml)));

             // Find the Endpoint element (in AggregateComponents namespace)
             NodeList endpoints = doc.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents", "Endpoint");
             
             if (endpoints.getLength() > 0) {
                 Element endpoint = (Element) endpoints.item(0);
                 
                 // Find the Certificate element within Endpoint (in AggregateComponents namespace)
                 NodeList certificates = endpoint.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents", "Certificate");
                 
                 if (certificates.getLength() > 0) {
                     Element certificate = (Element) certificates.item(0);
                     
                     // Find the ContentBinaryObject element within Certificate (in BasicComponents namespace)
                     NodeList contentObjects = certificate.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents", "ContentBinaryObject");
                     
                      if (contentObjects.getLength() > 0) {
                          String certBase64 = contentObjects.item(0).getTextContent();
                          if (certBase64 != null && !certBase64.trim().isEmpty()) {
                              try {
                                  // Remove all whitespace (including newlines and spaces) from base64 string
                                  // XML text content may contain formatting whitespace that needs to be stripped
                                  String cleanedBase64 = certBase64.replaceAll("\\s+", "");
                                  byte[] decodedCert = java.util.Base64.getDecoder().decode(cleanedBase64);
                                 java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                                 X509Certificate cert = (X509Certificate) cf.generateCertificate(
                                     new java.io.ByteArrayInputStream(decodedCert)
                                 );
                                 logger.info("Successfully extracted X.509 certificate from SMP Endpoint: Subject={}, Issuer={}",
                                     cert.getSubjectX500Principal(), cert.getIssuerX500Principal());
                                 return cert;
                             } catch (Exception e) {
                                 logger.warn("Failed to parse X509Certificate from SMP Endpoint: {}", e.getMessage());
                                 return null;
                             }
                         }
                     }
                 }
             }

             logger.debug("No certificate found in SMP ServiceMetadata Endpoint");
             return null;

         } catch (Exception e) {
             logger.warn("Failed to extract certificate from ServiceMetadata XML: {}", e.getMessage());
             return null;
         }
     }

    /**
     * Extracts the endpoint URL from SMP ServiceMetadata XML response
     *
     * According to DBNA SMP Profile v1.0, the AddressURI contains the receiver's endpoint.
     *
     * XML Structure: ServiceMetadata contains ProcessMetadata with Endpoint elements,
     * each containing an AddressURI with the endpoint URL.
     */
    private String extractEndpointFromXML(String xml) {
        if (xml == null) return null;
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // Get all Endpoint elements from AggregateComponents namespace
            // Using namespace: http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents
            NodeList endpoints = doc.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents", "Endpoint");

            logger.debug("Found {} Endpoint elements in ServiceMetadata", endpoints.getLength());

            // Extract endpoint URLs from each Endpoint element
            for (int i = 0; i < endpoints.getLength(); i++) {
                Element endpoint = (Element) endpoints.item(i);

                // Get the AddressURI element within Endpoint
                // Using namespace: http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents
                NodeList addressUris = endpoint.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents", "AddressURI");

                if (addressUris.getLength() > 0) {
                    String url = addressUris.item(0).getTextContent();
                    logger.debug("Found endpoint URL: {}", url);

                    if (url != null && !url.trim().isEmpty()) {
                        // DBNA requires HTTPS endpoints
                        if (url.startsWith("https://")) {
                            logger.info("Found HTTPS endpoint: {}", url);
                            return url;
                        }
                    }
                }
            }

            // Fallback: if no HTTPS endpoint found, return the first endpoint (not recommended but handle gracefully)
            if (endpoints.getLength() > 0) {
                Element endpoint = (Element) endpoints.item(0);
                NodeList addressUris = endpoint.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents", "AddressURI");
                if (addressUris.getLength() > 0) {
                    String url = addressUris.item(0).getTextContent();
                    logger.warn("No HTTPS endpoint found, using: {}", url);
                    return url;
                }
            }

            logger.warn("Could not extract any endpoint from ServiceMetadata response");
            return null;

        } catch (Exception e) {
            logger.error("Failed to parse ServiceMetadata XML: {}", e.getMessage(), e);
            return null;
        }
    }
    
     /**
      * Ensures that the SMP URL has a proper scheme (https://)
      * If the URL doesn't start with a scheme, prepends https:// (DBNA requires HTTPS)
      */
     private String ensureUrlScheme(String url) {
         if (url == null || url.trim().isEmpty()) {
             return url;
         }
         
         url = url.trim();
         
         // Check if URL already has a scheme
         if (url.startsWith("http://") || url.startsWith("https://")) {
             return url;
         }
         
         // No scheme present, prepend https:// (DBNA requires HTTPS)
         String urlWithScheme = "https://" + url;
         logger.debug("Added https:// scheme to SMP endpoint URL: {} -> {}", url, urlWithScheme);
         return urlWithScheme;
     }
     
     /**
      * URL-encodes a string for use in SMP URLs
      */
     private String urlEncode(String value) {
         try {
             return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
         } catch (Exception e) {
             logger.warn("Failed to URL encode value: {}", value);
             return value;
         }
     }

     /**
      * Builds the ServiceGroup URL for querying SMP resources
      *
      * @param smpEndpoint The base URL of the SMP service
      * @param participantId The participant identifier
      * @return The constructed ServiceGroup URL
      */
     private String buildServiceGroupUrl(String smpEndpoint, String participantId) {
         return smpEndpoint.replaceAll("/+$", "") + "/" + urlEncode(participantId);
     }

     /**
      * Builds the ServiceMetadata URL for querying SMP resources
      *
      * @param smpEndpoint The base URL of the SMP service
      * @param participantId The participant identifier
      * @param serviceReference The service reference (document type ID)
      * @return The constructed ServiceMetadata URL
      */
     private String buildServiceMetadataUrl(String smpEndpoint, String participantId, String serviceReference) {
         return smpEndpoint.replaceAll("/+$", "") + "/" + urlEncode(participantId) + "/services/" + urlEncode(serviceReference);
     }

    /**
     * Extracts the serviceReference from ServiceGroup XML for the requested document type
     *
     * The serviceReference is the exact document type ID as published in the SMP, which ensures
     * all special characters (including ##) are properly preserved and formatted.
     *
     * @return the serviceReference matching the documentTypeId, or null if not found
     */
    private String extractServiceReferenceFromServiceGroup(String xml, String documentTypeId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // Get all ServiceReference elements
            // Using namespace: http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents
            NodeList serviceReferences = doc.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents", "ServiceReference");

            logger.debug("Found {} ServiceReference elements in ServiceGroup", serviceReferences.getLength());

            // Check each ServiceReference for matching document type ID
            for (int i = 0; i < serviceReferences.getLength(); i++) {
                Element serviceRef = (Element) serviceReferences.item(i);

                // Get the ID element within ServiceReference
                // Using namespace: http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents
                NodeList idElements = serviceRef.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents", "ID");

                if (idElements.getLength() > 0) {
                    Element idElement = (Element) idElements.item(0);
                    String supportedDocType = idElement.getTextContent();
                    String schemeID = idElement.getAttribute("schemeID");

                    logger.debug("Found supported document type: {} (schemeID: {})", supportedDocType, schemeID);

                    // Check if this matches the requested document type
                    if (supportedDocType != null && supportedDocType.equals(documentTypeId)) {
                        // Return the serviceReference as schemeID::documentTypeId
                        String serviceReference = schemeID != null && !schemeID.isEmpty()
                            ? schemeID + "::" + supportedDocType
                            : supportedDocType;
                        logger.info("Found matching serviceReference for document type {}: {}", documentTypeId, serviceReference);
                        return serviceReference;
                    }
                }
            }

            logger.warn("Document type {} is not in the list of supported document types", documentTypeId);
            return null;

        } catch (Exception e) {
            logger.error("Failed to parse ServiceGroup XML: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parses ServiceGroup XML and checks if the specified document type is supported
     *
     * XML Structure:
     * <b2sg:ServiceGroup>
     *   <sma:ServiceReference>
     *     <smb:ID schemeID="bdx-docid-qns">urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##DBNAlliance-1.0-data-Core</smb:ID>
     *   </sma:ServiceReference>
     * </b2sg:ServiceGroup>
     */
    private boolean isDocumentTypeSupportedInServiceGroup(String xml, String documentTypeId) {
        return extractServiceReferenceFromServiceGroup(xml, documentTypeId) != null;
    }
    
    /**
     * Extracts all available service references from ServiceGroup XML
     * 
     * @param smpEndpoint The base URL of the SMP service
     * @param participantId The participant identifier
     * @return Map of document type ID to service reference
     * @throws Exception if retrieval fails
     */
    public Map<String, String> getAllServiceReferences(String smpEndpoint, String participantId) throws Exception {
        if (smpEndpoint == null || smpEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("SMP endpoint is required");
        }
        
         smpEndpoint = ensureUrlScheme(smpEndpoint);

         try {
             String serviceGroupUrl = buildServiceGroupUrl(smpEndpoint, participantId);
             logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);

             String response = executeHttpGet(serviceGroupUrl);
             logger.debug("ServiceGroup resource retrieved successfully");

             return extractAllServiceReferencesFromServiceGroup(response);
        } catch (SMPDiscoveryException e) {
            logger.error("Failed to retrieve all service references from SMP: HTTP {} from {}",
                e.getSmpHttpStatusCode(), e.getSmpEndpoint(), e);
            throw new Exception("Failed to retrieve service references (HTTP " + e.getSmpHttpStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to retrieve all service references from SMP", e);
            throw new Exception("Failed to retrieve service references: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts all available service references from ServiceGroup XML and returns both services and raw XML
     * 
     * @param smpEndpoint The base URL of the SMP service
     * @param participantId The participant identifier
     * @return Map containing:
     *   - "services": Map of document type ID to service reference
     *   - "serviceGroupXml": The raw ServiceGroup XML response
     * @throws Exception if retrieval fails
     */
    public Map<String, Object> getAllServiceReferencesWithXml(String smpEndpoint, String participantId) throws Exception {
        if (smpEndpoint == null || smpEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("SMP endpoint is required");
        }
        
         smpEndpoint = ensureUrlScheme(smpEndpoint);

         try {
             String serviceGroupUrl = buildServiceGroupUrl(smpEndpoint, participantId);
             logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);

             String response = executeHttpGet(serviceGroupUrl);
             logger.debug("ServiceGroup resource retrieved successfully");

             Map<String, String> services = extractAllServiceReferencesFromServiceGroup(response);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("services", services);
            result.put("serviceGroupXml", response);
            
            return result;
        } catch (SMPDiscoveryException e) {
            logger.error("Failed to retrieve all service references from SMP: HTTP {} from {}",
                e.getSmpHttpStatusCode(), e.getSmpEndpoint(), e);
            throw new Exception("Failed to retrieve service references (HTTP " + e.getSmpHttpStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to retrieve all service references from SMP", e);
            throw new Exception("Failed to retrieve service references: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts all service references from ServiceGroup XML
     * 
     * @param xml The ServiceGroup XML response
     * @return Map of document type ID to service reference
     */
    private Map<String, String> extractAllServiceReferencesFromServiceGroup(String xml) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            
            // Get all ServiceReference elements
            NodeList serviceReferences = doc.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/AggregateComponents", "ServiceReference");
            
            logger.debug("Found {} ServiceReference elements in ServiceGroup", serviceReferences.getLength());
            
            // Extract all ServiceReference IDs
            for (int i = 0; i < serviceReferences.getLength(); i++) {
                Element serviceRef = (Element) serviceReferences.item(i);
                
                // Get the ID element within ServiceReference
                NodeList idElements = serviceRef.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/BasicComponents", "ID");
                
                if (idElements.getLength() > 0) {
                    Element idElement = (Element) idElements.item(0);
                    String documentTypeId = idElement.getTextContent();
                    String schemeID = idElement.getAttribute("schemeID");
                    
                    logger.debug("Found document type: {} (schemeID: {})", documentTypeId, schemeID);
                    
                    // Return the serviceReference as schemeID::documentTypeId
                    String serviceReference = schemeID != null && !schemeID.isEmpty()
                        ? schemeID + "::" + documentTypeId
                        : documentTypeId;
                    
                    result.put(documentTypeId, serviceReference);
                }
            }
            
            logger.info("Extracted {} document types from ServiceGroup", result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to parse ServiceGroup XML: {}", e.getMessage(), e);
            return result;
        }
    }

    /**
     * Clears expired cache entries
     */
    public void clearExpiredCache() {
        long now = Instant.now().toEpochMilli();
        resourceCache.entrySet().stream()
            .filter(entry -> (now - entry.getValue().cachedAt) > CACHE_EXPIRY_MS)
            .forEach(entry -> {
                logger.debug("Removing expired cache entry: {}", entry.getKey());
                resourceCache.remove(entry.getKey());
            });
    }
    
    /**
     * Internal class for caching SMP resources with Last-Modified headers
     */
    private static class CachedSMPResource {
        final String content;
        final String lastModified;
        final long cachedAt;
        
        CachedSMPResource(String content, String lastModified, long cachedAt) {
            this.content = content;
            this.lastModified = lastModified;
            this.cachedAt = cachedAt;
        }
    }
}





