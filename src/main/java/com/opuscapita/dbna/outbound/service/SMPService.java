package com.opuscapita.dbna.outbound.service;

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
import java.time.Instant;
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
     * 
     * @param smpEndpoint The base URL of the SMP service
     * @param participantId The participant identifier (scheme::id)
     * @param documentTypeId The document type identifier
     * @param processId The process identifier
     * @return The service endpoint URL for sending, or null if not found
     * @throws Exception if service discovery fails
     */
    public String discoverServiceEndpoint(String smpEndpoint, String participantId, 
                                          String documentTypeId, String processId) throws Exception {
        if (smpEndpoint == null || smpEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("SMP endpoint is required");
        }

        logger.info("Discovering service endpoint from SMP: {}", smpEndpoint);
        logger.debug("Participant: {}, DocumentType: {}, Process: {}", participantId, documentTypeId, processId);
        
        try {
            // Step 1: Query ServiceGroup to verify document type support
            if (!isDocumentTypeSupported(smpEndpoint, participantId, documentTypeId)) {
                logger.warn("Document type {} not supported by participant {}", documentTypeId, participantId);
                return null;
            }
            
            // Step 2: Query ServiceMetadata to get endpoint information
            String endpoint = queryServiceEndpoint(smpEndpoint, documentTypeId, processId);

            if (endpoint != null) {
                logger.info("Successfully discovered service endpoint: {}", endpoint);
                return endpoint;
            } else {
                logger.warn("No service endpoint found for document type: {}, process: {}", documentTypeId, processId);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error discovering service endpoint from SMP", e);
            throw new Exception("SMP service discovery failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if a document type is supported by querying the ServiceGroup resource
     */
    private boolean isDocumentTypeSupported(String smpEndpoint, String participantId, String documentTypeId) {
        String serviceGroupUrl = smpEndpoint.replaceAll("/+$", "") + "/" + urlEncode(participantId);
        
        logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);
        
        try {
            String response = executeHttpGet(serviceGroupUrl);
            logger.debug("ServiceGroup resource retrieved successfully");

            // Parse XML and check if document type is supported
            return isDocumentTypeSupportedInServiceGroup(response, documentTypeId);
        } catch (Exception e) {
            logger.warn("Failed to retrieve ServiceGroup resource: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Queries the ServiceMetadata resource to get the endpoint for a specific document type and process
     */
    private String queryServiceEndpoint(String smpEndpoint, String documentTypeId, String processId) {
        String serviceMetadataUrl = smpEndpoint.replaceAll("/+$", "") + "/services/" +
            urlEncode(documentTypeId) + "/processes/" + urlEncode(processId) + "/endpoints";
        
        logger.debug("Querying ServiceMetadata resource: {}", serviceMetadataUrl);
        
        try {
            String response = executeHttpGet(serviceMetadataUrl);
            logger.debug("ServiceMetadata resource retrieved successfully");

            // Parse endpoint from response XML
            return extractEndpointFromXML(response);
        } catch (Exception e) {
            logger.warn("Failed to retrieve ServiceMetadata resource: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Executes an HTTP GET request with caching support
     */
    private String executeHttpGet(String url) throws IOException {
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
                
                throw new IOException("HTTP " + statusCode + " response from SMP service");
            });
            
            return response;
        } finally {
            httpGet.reset();
        }
    }
    
    /**
     * Extracts the endpoint URL from SMP ServiceMetadata XML response
     * 
     * XML Structure:
     * <smd:ServiceMetadata xmlns:smd="http://docs.oasis-open.org/bdxr/ns/SMP/2/ServiceMetadata">
     *   <smd:ServiceInformation>
     *     <smd:ProcessList>
     *       <smd:Process>
     *         <smd:ServiceEndpointList>
     *           <smd:Endpoint transport="https">https://example.com/as4</smd:Endpoint>
     *         </smd:ServiceEndpointList>
     *       </smd:Process>
     *     </smd:ProcessList>
     *   </smd:ServiceInformation>
     * </smd:ServiceMetadata>
     */
    private String extractEndpointFromXML(String xml) {
        if (xml == null) return null;
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // Get all Endpoint elements (DBNA requires HTTPS)
            // Using namespace: http://docs.oasis-open.org/bdxr/ns/SMP/2/ServiceMetadata
            NodeList endpoints = doc.getElementsByTagNameNS("http://docs.oasis-open.org/bdxr/ns/SMP/2/ServiceMetadata", "Endpoint");

            logger.debug("Found {} Endpoint elements in ServiceMetadata", endpoints.getLength());

            // Prefer HTTPS endpoints as required by DBNA
            for (int i = 0; i < endpoints.getLength(); i++) {
                Element endpoint = (Element) endpoints.item(i);
                String transport = endpoint.getAttribute("transport");
                String url = endpoint.getTextContent();

                logger.debug("Found endpoint with transport={}, url={}", transport, url);

                if ("https".equalsIgnoreCase(transport)) {
                    logger.info("Found HTTPS endpoint: {}", url);
                    return url;
                }
            }

            // Fallback to HTTP if no HTTPS found (not recommended but handle gracefully)
            if (endpoints.getLength() > 0) {
                Element endpoint = (Element) endpoints.item(0);
                String url = endpoint.getTextContent();
                logger.warn("No HTTPS endpoint found, using: {}", url);
                return url;
            }

            logger.warn("Could not extract any endpoint from ServiceMetadata response");
            return null;

        } catch (Exception e) {
            logger.error("Failed to parse ServiceMetadata XML: {}", e.getMessage(), e);
            return null;
        }
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

                    logger.debug("Found supported document type: {}", supportedDocType);

                    // Check if this matches the requested document type
                    if (supportedDocType != null && supportedDocType.equals(documentTypeId)) {
                        logger.info("Document type {} is supported", documentTypeId);
                        return true;
                    }
                }
            }

            logger.warn("Document type {} is not in the list of supported document types", documentTypeId);
            return false;

        } catch (Exception e) {
            logger.error("Failed to parse ServiceGroup XML: {}", e.getMessage(), e);
            return false;
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





