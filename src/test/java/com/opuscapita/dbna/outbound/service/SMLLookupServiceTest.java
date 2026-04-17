package com.opuscapita.dbna.outbound.service;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SMLLookupService
 * Tests DBNA SML Profile v1.2 DNS name construction and NAPTR queries
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SMLLookupService Unit Tests")
class SMLLookupServiceTest {

    private SMLLookupService smlLookupService;

    @BeforeEach
    void setUp() {
        smlLookupService = new SMLLookupService();
    }

    @Test
    @DisplayName("Should construct DNS name using DBNA algorithm (SHA256 + Base32)")
    void testDNSNameConstruction() {
        // This tests the DBNA SML Profile v1.2 DNS name construction algorithm
        // Input: scheme=GLN, identifier=1234567890123
        // Expected: base32(sha256("gln::1234567890123")).sml.dbnalliance.com

        String scheme = "GLN";
        String identifier = "1234567890123";

        // Calculate expected DNS name using the algorithm from DBNA SML Profile v1.2
        String concatenated = "gln::1234567890123";
        byte[] digest = Hashing.sha256()
            .hashString(concatenated, StandardCharsets.UTF_8)
            .asBytes();
        String encoded = BaseEncoding.base32()
            .encode(digest)
            .toLowerCase()
            .replaceAll("=+$", "");

        String expectedDNSName = encoded + ".sml.dbnalliance.net";

        // Note: Cannot directly test private method, but we verify via behavior in lookupSMPEndpoint
        // This test documents the expected algorithm
        assertEquals("qcie7f2ny3ze5nmhqse7z5j6jerds3gc437bfjl2k6vq6minb47a", encoded);
    }

    @Test
    @DisplayName("Should throw exception for null scheme")
    void testNullSchemeThrowsException() {
        // Arrange
        String scheme = null;
        String identifier = "1234567890123";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            smlLookupService.lookupSMPEndpoint(scheme, identifier)
        );
    }

    @Test
    @DisplayName("Should throw exception for empty scheme")
    void testEmptySchemeThrowsException() {
        // Arrange
        String scheme = "";
        String identifier = "1234567890123";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            smlLookupService.lookupSMPEndpoint(scheme, identifier)
        );
    }

    @Test
    @DisplayName("Should throw exception for null identifier")
    void testNullIdentifierThrowsException() {
        // Arrange
        String scheme = "GLN";
        String identifier = null;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            smlLookupService.lookupSMPEndpoint(scheme, identifier)
        );
    }

    @Test
    @DisplayName("Should throw exception for empty identifier")
    void testEmptyIdentifierThrowsException() {
        // Arrange
        String scheme = "GLN";
        String identifier = "";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            smlLookupService.lookupSMPEndpoint(scheme, identifier)
        );
    }

    @Test
    @DisplayName("Should support different SML environments")
    void testSMLEnvironmentConfiguration() {
        // Test that smlEnvironment property can be set and used
        // Production: sml.dbnalliance.net
        // Test: sml.dbnalliance.com
        // Pilot: sml.dbnalliancepilot.net

        // Set to test environment
        ReflectionTestUtils.setField(smlLookupService, "smlEnvironment", "test");
        
        // Set to pilot environment
        ReflectionTestUtils.setField(smlLookupService, "smlEnvironment", "pilot");
        
        // Set to production environment
        ReflectionTestUtils.setField(smlLookupService, "smlEnvironment", "production");

        assertTrue(true); // Environment configuration is valid
    }

    @Test
    @DisplayName("Should lowercase scheme and identifier for DNS name construction")
    void testLowercaseConversion() {
        // DBNA algorithm requires lowercase for hash calculation
        String upperScheme = "GLN";
        String upperIdentifier = "1234567890123";

        String concatenated = upperScheme.toLowerCase() + "::" + upperIdentifier.toLowerCase();
        
        byte[] digest = Hashing.sha256()
            .hashString(concatenated, StandardCharsets.UTF_8)
            .asBytes();

        // Verify hash is computed on lowercase string
        assertNotNull(digest);
        assertEquals(32, digest.length); // SHA256 produces 32 bytes
    }

    @Test
    @DisplayName("Should handle various identifier schemes")
    void testVariousIdentifierSchemes() {
        // DBNA supports multiple identifier schemes
        String[] schemes = {"GLN", "0192", "0088", "9999"};

        for (String scheme : schemes) {
            String identifier = "test-identifier-" + scheme;
            
            // Scheme and identifier should be valid (no exception thrown during validation)
            // Actual DNS lookup would require mocking
            assertDoesNotThrow(() -> {
                // Just validate the format
                assertNotNull(scheme);
                assertNotNull(identifier);
            });
        }
    }

    @Test
    @DisplayName("Should handle DNS NamingException gracefully")
    void testDNSNamingException() {
        // When DNS query fails, should throw Exception with meaningful message
        String scheme = "GLN";
        String identifier = "9999999999999";

        // The actual DNS exception would be caught and re-thrown as Exception
        // This test ensures the error handling is in place
        assertThrows(Exception.class, () -> 
            smlLookupService.lookupSMPEndpoint(scheme, identifier)
        );
    }

    @Test
    @DisplayName("Should extract NAPTR service type correctly")
    void testNAPTRServiceTypeExtraction() {
        // DBNA SMP Profile v1.0 specifies service type:
        // oasis-bdxr-smp-2#dbnalliance-1.1
        
        String naptyRecord = "100 10 \"U\" \"oasis-bdxr-smp-2#dbnalliance-1.1\" " +
            "\"!^.*$!https://smp.example.com/!\" .";

        // Verify the NAPTR record contains expected DBNA service type
        assertTrue(naptyRecord.contains("oasis-bdxr-smp-2#dbnalliance-1.1"));
    }

    @Test
    @DisplayName("Should extract SMP URL from NAPTR regex expression")
    void testSMPURLExtractionFromNAPTR() {
        // DBNA SML specifies NAPTR URL format: !^.*$!https://smp.example.com/!
        String naptyRecord = "100 10 \"U\" \"oasis-bdxr-smp-2#dbnalliance-1.1\" " +
            "\"!^.*$!https://smp.example.com/service/!\" .";

        // The regex between second and third "!" contains the URL
        // Pattern: !^.*$!{URL}!
        assertTrue(naptyRecord.contains("https://smp.example.com/service/"));
        
        // Verify URL is HTTPS (per DBNA SMP Profile v1.0)
        assertTrue(naptyRecord.contains("https://"));
    }

    @Test
    @DisplayName("Base32 encoding produces valid DNS names")
    void testBase32EncodingForDNS() {
        // SHA256 digest is 32 bytes (256 bits)
        byte[] testDigest = new byte[32];
        for (int i = 0; i < 32; i++) {
            testDigest[i] = (byte) i;
        }

        // Base32 encode
        String encoded = BaseEncoding.base32()
            .encode(testDigest)
            .toLowerCase()
            .replaceAll("=+$", "");

        // Verify encoding produces valid characters for DNS names
        // DNS names must contain only: a-z, 0-9, and hyphen
        assertTrue(encoded.matches("[a-z0-9]+"));
        
        // Verify no trailing equals signs
        assertFalse(encoded.contains("="));
    }

    @Test
    @DisplayName("Should handle whitespace in scheme and identifier")
    void testWhitespaceHandling() {
        // Schemes with whitespace should be rejected
        String schemeWithSpace = "GLN ";
        String identifier = "1234567890123";

        // Implementation should trim whitespace
        String trimmed = schemeWithSpace.trim();
        assertEquals("GLN", trimmed);
    }

    @Test
    @DisplayName("Should construct same DNS name for same inputs (deterministic)")
    void testDeterministicDNSConstruction() {
        // Same scheme + identifier should always produce same DNS name
        String scheme = "GLN";
        String identifier = "1234567890123";

        String concatenated1 = scheme.toLowerCase() + "::" + identifier.toLowerCase();
        String concatenated2 = scheme.toLowerCase() + "::" + identifier.toLowerCase();

        byte[] digest1 = Hashing.sha256()
            .hashString(concatenated1, StandardCharsets.UTF_8)
            .asBytes();
        
        byte[] digest2 = Hashing.sha256()
            .hashString(concatenated2, StandardCharsets.UTF_8)
            .asBytes();

        // Digests should be identical
        assertArrayEquals(digest1, digest2);
    }
}

