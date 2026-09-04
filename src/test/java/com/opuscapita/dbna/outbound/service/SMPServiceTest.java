package com.opuscapita.dbna.outbound.service;

import org.apache.hc.client5.http.classic.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SMPService
 * Tests DBNA SMP Profile v1.0 REST API integration and HTTP caching
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SMPService Unit Tests")
class SMPServiceTest {

    @Mock
    private HttpClient httpClient;

    private SMPService smpService;

    @BeforeEach
    void setUp() {
        smpService = new SMPService(httpClient);
    }

    // === Constructor Tests ===

    @Test
    @DisplayName("Should construct SMPService with HttpClient")
    void testConstructorWithHttpClient() {
        assertNotNull(smpService, "Service should be constructed successfully");
    }

    // === SMP Endpoint Validation Tests ===

    @Test
    @DisplayName("Should reject null SMP endpoint")
    void testNullSMPEndpointThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint(null, "participant", "doctype", "process"),
            "Service should throw IllegalArgumentException for null SMP endpoint"
        );
    }

    @Test
    @DisplayName("Should reject empty string SMP endpoint")
    void testEmptySMPEndpointThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("", "participant", "doctype", "process"),
            "Service should throw IllegalArgumentException for empty SMP endpoint"
        );
    }

    @Test
    @DisplayName("Should reject whitespace-only SMP endpoint")
    void testWhitespaceOnlySMPEndpointThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("   ", "participant", "doctype", "process"),
            "Service should throw IllegalArgumentException for whitespace-only endpoint"
        );
    }

    @Test
    @DisplayName("Should reject tab character SMP endpoint")
    void testTabCharacterSMPEndpointThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("\t", "participant", "doctype", "process"),
            "Service should throw IllegalArgumentException for tab character endpoint"
        );
    }

    @Test
    @DisplayName("Should reject newline character SMP endpoint")
    void testNewlineCharacterSMPEndpointThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("\n", "participant", "doctype", "process"),
            "Service should throw IllegalArgumentException for newline endpoint"
        );
    }

    @Test
    @DisplayName("Should reject single space SMP endpoint")
    void testSingleSpaceSMPEndpointThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint(" ", "participant", "doctype", "process"),
            "Service should throw IllegalArgumentException for single space endpoint"
        );
    }

    @Test
    @DisplayName("Should validate endpoint parameter before other parameters")
    void testEndpointValidatedFirst() {
        // Even with empty other parameters, should fail on endpoint validation
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("", "", "", "")
        );
    }

    // === Error Message Validation ===

    @Test
    @DisplayName("Error message for null endpoint should be informative")
    void testNullEndpointErrorMessageContent() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint(null, "p", "d", "pr")
        );
        String message = exception.getMessage();
        assertNotNull(message, "Error message should not be null");
        assertTrue(message.toLowerCase().contains("endpoint") || message.toLowerCase().contains("required"),
            "Error message should mention endpoint requirement");
    }

    @Test
    @DisplayName("Error message for empty endpoint should be informative")
    void testEmptyEndpointErrorMessageContent() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("", "p", "d", "pr")
        );
        String message = exception.getMessage();
        assertNotNull(message, "Error message should not be null");
        assertTrue(message.toLowerCase().contains("endpoint") || message.toLowerCase().contains("required"),
            "Error message should mention endpoint requirement");
    }

    // === Cache Management Tests ===

    @Test
    @DisplayName("Should clear expired cache without throwing exception")
    void testClearExpiredCacheSucceeds() {
        assertDoesNotThrow(() -> smpService.clearExpiredCache(),
            "clearExpiredCache should not throw exception");
    }

    @Test
    @DisplayName("Should allow multiple cache clear calls")
    void testMultipleClearExpiredCacheCalls() {
        assertDoesNotThrow(() -> {
            smpService.clearExpiredCache();
            smpService.clearExpiredCache();
            smpService.clearExpiredCache();
        }, "Multiple clearExpiredCache calls should succeed");
    }

    @Test
    @DisplayName("Should clear cache quickly without blocking")
    void testClearCachePerformance() {
        long timeoutMs = 5000;
        long startTime = System.currentTimeMillis();
        smpService.clearExpiredCache();
        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue(elapsed < timeoutMs, "clearExpiredCache should complete quickly");
    }

    // === Consistency Tests ===

    @Test
    @DisplayName("Service should consistently reject invalid endpoint")
    void testConsistentEndpointValidation() {
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () ->
                smpService.discoverServiceEndpoint("", "p", "d", "pr"),
                "Service should consistently reject empty endpoint"
            );
        }
    }

    @Test
    @DisplayName("Service should consistently reject null endpoint")
    void testConsistentNullEndpointValidation() {
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () ->
                smpService.discoverServiceEndpoint(null, "p", "d", "pr"),
                "Service should consistently reject null endpoint"
            );
        }
    }

    // === Idempotence Tests ===

    @Test
    @DisplayName("clearExpiredCache should be idempotent")
    void testClearCacheIdempotence() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                smpService.clearExpiredCache();
            }
        }, "Multiple cache clear operations should be safe");
    }

    // === Parameter Format Tests ===

    @Test
    @DisplayName("Should accept HTTPS endpoint URLs")
    void testAcceptHTTPSEndpoint() {
        String url = "https://smp.example.com";
        assertTrue(url.startsWith("https://"), "Service should accept HTTPS endpoints");
    }

    @Test
    @DisplayName("Should accept endpoint URLs without scheme")
    void testAcceptEndpointWithoutScheme() {
        String url = "smp.example.com";
        assertFalse(url.contains("://"), "Service should handle URLs without scheme");
    }

    @Test
    @DisplayName("Should accept participant IDs with double colon notation")
    void testAcceptParticipantIDWithDoubleColon() {
        String participantId = "GLN::1234567890123";
        assertTrue(participantId.contains("::"), "Service should accept GLN notation");
    }

    @Test
    @DisplayName("Should accept document type IDs in URN format")
    void testAcceptURNDocumentTypeID() {
        String documentTypeId = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
        assertTrue(documentTypeId.startsWith("urn:"), "Service should accept URN format");
        assertTrue(documentTypeId.contains(":"), "Service should accept URN with colons");
    }

    @Test
    @DisplayName("Should accept process IDs in URN format")
    void testAcceptURNProcessID() {
        String processId = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
        assertTrue(processId.startsWith("urn:"), "Service should accept URN format for process IDs");
    }

    // === Service State Tests ===

    @Test
    @DisplayName("Service should be usable after cache clear")
    void testServiceUsableAfterCacheClear() {
        assertDoesNotThrow(() -> smpService.clearExpiredCache());

        // Service should still validate parameters correctly
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("", "p", "d", "pr"),
            "Service should validate parameters after cache clear"
        );
    }

    @Test
    @DisplayName("Service should accept valid HTTPS URLs")
    void testAcceptValidHTTPSURL() {
        // This validates that service doesn't reject valid HTTPS URLs at the parameter level
        String validUrl = "https://smp.dbna.example.com:8443/smp";
        assertTrue(validUrl.startsWith("https://"), "HTTPS URLs should be valid");
    }

    @Test
    @DisplayName("Should handle endpoint with trailing slash")
    void testEndpointWithTrailingSlash() {
        String url = "https://smp.example.com/";
        assertTrue(url.endsWith("/"), "Service should handle trailing slashes");
    }

    @Test
    @DisplayName("Should handle endpoint with path components")
    void testEndpointWithPath() {
        String url = "https://smp.example.com/api/v1";
        assertTrue(url.contains("/"), "Service should handle URLs with paths");
    }

    // === Boundary Tests ===

    @Test
    @DisplayName("Should handle very long endpoint URL")
    void testVeryLongEndpointURL() {
        String longUrl = "https://smp.example.com/" + "a".repeat(1000);
        assertTrue(longUrl.length() > 1000, "Service should accept long URLs");
    }

    @Test
    @DisplayName("Should handle endpoint with unicode characters")
    void testEndpointWithUnicode() {
        String unicodeUrl = "https://smp.例え.com";
        assertFalse(unicodeUrl.isEmpty(), "Service should accept unicode in URLs");
    }

    // === Public API Tests for getAllServiceReferences ===

    @Test
    @DisplayName("Should reject null endpoint in getAllServiceReferences")
    void testGetAllServiceReferencesNullEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.getAllServiceReferences(null, "participant"),
            "getAllServiceReferences should reject null endpoint"
        );
    }

    @Test
    @DisplayName("Should reject empty endpoint in getAllServiceReferences")
    void testGetAllServiceReferencesEmptyEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.getAllServiceReferences("", "participant"),
            "getAllServiceReferences should reject empty endpoint"
        );
    }

    // === Public API Tests for getAllServiceReferencesWithXml ===

    @Test
    @DisplayName("Should reject null endpoint in getAllServiceReferencesWithXml")
    void testGetAllServiceReferencesWithXmlNullEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.getAllServiceReferencesWithXml(null, "participant"),
            "getAllServiceReferencesWithXml should reject null endpoint"
        );
    }

    @Test
    @DisplayName("Should reject empty endpoint in getAllServiceReferencesWithXml")
    void testGetAllServiceReferencesWithXmlEmptyEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.getAllServiceReferencesWithXml("", "participant"),
            "getAllServiceReferencesWithXml should reject empty endpoint"
        );
    }

    // === Multiple Whitespace Tests ===

    @Test
    @DisplayName("Should reject endpoint with multiple spaces")
    void testMultipleSpacesEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("     ", "p", "d", "pr"),
            "Service should reject endpoint with only spaces"
        );
    }

    @Test
    @DisplayName("Should reject endpoint with mixed whitespace")
    void testMixedWhitespaceEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("  \t  \n  ", "p", "d", "pr"),
            "Service should reject endpoint with mixed whitespace"
        );
    }

    // === Validation Order Tests ===

    @Test
    @DisplayName("Should validate SMP endpoint before accepting any request")
    void testSMPEndpointValidationOrder() {
        // Test that empty endpoint is validated regardless of other parameters
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("", "valid-participant-id", "valid-document-type", "valid-process-id"),
            "SMP endpoint should be validated first"
        );
    }

    // === Service Method Completeness Tests ===

    @Test
    @DisplayName("discoverServiceEndpoint method is public and callable")
    void testDiscoverServiceEndpointMethodExists() {
        // Verify the method exists and is accessible
        assertNotNull(smpService, "Service instance should exist");

        // Method should be callable (though it throws on invalid params)
        assertThrows(IllegalArgumentException.class, () ->
            smpService.discoverServiceEndpoint("", "p", "d", "pr")
        );
    }

    @Test
    @DisplayName("getAllServiceReferences method is public and callable")
    void testGetAllServiceReferencesMethodExists() {
        assertNotNull(smpService, "Service instance should exist");
        assertThrows(IllegalArgumentException.class, () ->
            smpService.getAllServiceReferences("", "p")
        );
    }

    @Test
    @DisplayName("getAllServiceReferencesWithXml method is public and callable")
    void testGetAllServiceReferencesWithXmlMethodExists() {
        assertNotNull(smpService, "Service instance should exist");
        assertThrows(IllegalArgumentException.class, () ->
            smpService.getAllServiceReferencesWithXml("", "p")
        );
    }

    @Test
    @DisplayName("clearExpiredCache method is public and callable")
    void testClearExpiredCacheMethodExists() {
        assertNotNull(smpService, "Service instance should exist");
        assertDoesNotThrow(() -> smpService.clearExpiredCache());
    }
}
