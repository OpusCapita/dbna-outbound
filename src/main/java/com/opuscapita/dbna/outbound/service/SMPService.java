package com.opuscapita.dbna.outbound.service;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
            String endpoint = queryServiceEndpoint(smpEndpoint, participantId, documentTypeId, processId);
            
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
    private boolean isDocumentTypeSupported(String smpEndpoint, String participantId, String documentTypeId) 
            throws IOException {
        String serviceGroupUrl = smpEndpoint.replaceAll("/+$", "") + "/participants/" + 
            urlEncode(participantId);
        
        logger.debug("Querying ServiceGroup resource: {}", serviceGroupUrl);
        
        try {
            String response = executeHttpGet(serviceGroupUrl);
            // For now, assume document type is supported if we get a 200 response
            // A complete implementation would parse the XML and check for the specific document type
            logger.debug("ServiceGroup resource retrieved successfully");
            return true;
        } catch (Exception e) {
            logger.warn("Failed to retrieve ServiceGroup resource: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Queries the ServiceMetadata resource to get the endpoint for a specific document type and process
     */
    private String queryServiceEndpoint(String smpEndpoint, String participantId, 
                                        String documentTypeId, String processId) throws IOException {
        String serviceMetadataUrl = smpEndpoint.replaceAll("/+$", "") + "/services/" + 
            urlEncode(documentTypeId) + "/processes/" + urlEncode(processId) + "/endpoints";
        
        logger.debug("Querying ServiceMetadata resource: {}", serviceMetadataUrl);
        
        try {
            String response = executeHttpGet(serviceMetadataUrl);
            // Parse endpoint from response XML
            // This is a simplified implementation - a complete one would parse the XML properly
            logger.debug("ServiceMetadata resource retrieved successfully");
            
            // For now, return a placeholder - actual implementation would extract from XML
            // Expected structure: <Endpoint transport="https">https://example.com/as4</Endpoint>
            String endpoint = extractEndpointFromXML(response);
            return endpoint;
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
     * Simplified implementation - should be enhanced to properly parse XML
     */
    private String extractEndpointFromXML(String xml) {
        if (xml == null) return null;
        
        // Look for https endpoint first (DBNA requires HTTPS)
        int start = xml.indexOf("\"https://");
        if (start > -1) {
            int end = xml.indexOf("\"", start + 1);
            if (end > start) {
                return xml.substring(start + 1, end);
            }
        }
        
        logger.warn("Could not extract HTTPS endpoint from ServiceMetadata response");
        return null;
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





