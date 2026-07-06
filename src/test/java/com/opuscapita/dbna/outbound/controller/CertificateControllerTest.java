package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.config.AS4Configuration;
import com.opuscapita.dbna.outbound.util.CertificateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.KeyStore;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CertificateController
 * Tests certificate status, listing, and validation endpoints
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateController Unit Tests")
class CertificateControllerTest {

    @Mock
    private AS4Configuration as4Configuration;

    @Mock
    private KeyStore keyStore;

    private CertificateController controller;

    @BeforeEach
    void setUp() {
        controller = new CertificateController(as4Configuration);
    }

    @Test
    @DisplayName("Should return certificate status when keystore is configured")
    void testGetCertificateStatusSuccess() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");
        when(as4Configuration.getKeystorePassword()).thenReturn("password");
        when(as4Configuration.getKeystoreType()).thenReturn("JKS");
        when(as4Configuration.getKeyAlias()).thenReturn("test-alias");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(keyStore);
            certUtil.when(() -> CertificateUtil.validateCertificate(keyStore, "test-alias"))
                .thenReturn(true);
            certUtil.when(() -> CertificateUtil.isCertificateExpiringSoon(keyStore, "test-alias", 30))
                .thenReturn(false);
            certUtil.when(() -> CertificateUtil.getCertificateInfo(keyStore, "test-alias"))
                .thenReturn("Subject: CN=example.com");

            // Act
            ResponseEntity<Map<String, Object>> response = controller.getCertificateStatus();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue((boolean) response.getBody().get("keystoreConfigured"));
            assertTrue((boolean) response.getBody().get("certificateValid"));
            assertFalse((boolean) response.getBody().get("certificateExpiringSoon"));
            assertEquals("test-alias", response.getBody().get("certificateAlias"));
            assertEquals("Subject: CN=example.com", response.getBody().get("certificateInfo"));
        }
    }

    @Test
    @DisplayName("Should handle keystore load failure gracefully")
    void testGetCertificateStatusKeystoreLoadFailure() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");
        when(as4Configuration.getKeystorePassword()).thenReturn("password");
        when(as4Configuration.getKeystoreType()).thenReturn("JKS");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(null);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.getCertificateStatus();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Failed to load keystore", response.getBody().get("error"));
        }
    }

    @Test
    @DisplayName("Should report keystore not configured")
    void testGetCertificateStatusKeystoreNotConfigured() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(false);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getCertificateStatus();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((boolean) response.getBody().get("keystoreConfigured"));
    }

    @Test
    @DisplayName("Should handle exception during certificate status retrieval")
    void testGetCertificateStatusException() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured())
            .thenThrow(new RuntimeException("Configuration error"));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getCertificateStatus();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Configuration error", response.getBody().get("error"));
    }

    @Test
    @DisplayName("Should list certificate aliases successfully")
    void testListAliasesSuccess() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(keyStore);
            certUtil.when(() -> CertificateUtil.listAliases(keyStore))
                .thenReturn("alias1, alias2, alias3");

            // Act
            ResponseEntity<Map<String, Object>> response = controller.listAliases();

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("alias1, alias2, alias3", response.getBody().get("aliases"));
        }
    }

    @Test
    @DisplayName("Should reject list aliases when keystore not configured")
    void testListAliasesKeystoreNotConfigured() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(false);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.listAliases();

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Keystore not configured", response.getBody().get("error"));
    }

    @Test
    @DisplayName("Should handle keystore load failure in listAliases")
    void testListAliasesKeystoreLoadFailure() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(null);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.listAliases();

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Failed to load keystore", response.getBody().get("error"));
        }
    }

    @Test
    @DisplayName("Should handle exception during listAliases")
    void testListAliasesException() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured())
            .thenThrow(new RuntimeException("Keystore error"));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.listAliases();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    @DisplayName("Should validate certificate by alias successfully")
    void testValidateCertificateSuccess() {
        // Arrange
        String alias = "test-cert";
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(keyStore);
            certUtil.when(() -> CertificateUtil.validateCertificate(keyStore, alias))
                .thenReturn(true);
            certUtil.when(() -> CertificateUtil.isCertificateExpiringSoon(keyStore, alias, 30))
                .thenReturn(false);
            certUtil.when(() -> CertificateUtil.getCertificateInfo(keyStore, alias))
                .thenReturn("CN=test, O=DBNA");

            // Act
            ResponseEntity<Map<String, Object>> response = controller.validateCertificate(alias);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(alias, response.getBody().get("alias"));
            assertTrue((boolean) response.getBody().get("valid"));
            assertFalse((boolean) response.getBody().get("expiringSoon"));
        }
    }

    @Test
    @DisplayName("Should reject validate when keystore not configured")
    void testValidateCertificateKeystoreNotConfigured() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured()).thenReturn(false);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.validateCertificate("test-alias");

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Keystore not configured", response.getBody().get("error"));
    }

    @Test
    @DisplayName("Should handle invalid certificate in validation")
    void testValidateCertificateInvalid() {
        // Arrange
        String alias = "invalid-cert";
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(keyStore);
            certUtil.when(() -> CertificateUtil.validateCertificate(keyStore, alias))
                .thenReturn(false);
            certUtil.when(() -> CertificateUtil.isCertificateExpiringSoon(keyStore, alias, 30))
                .thenReturn(true);
            certUtil.when(() -> CertificateUtil.getCertificateInfo(keyStore, alias))
                .thenReturn("CN=expired, O=Other");

            // Act
            ResponseEntity<Map<String, Object>> response = controller.validateCertificate(alias);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse((boolean) response.getBody().get("valid"));
            assertTrue((boolean) response.getBody().get("expiringSoon"));
        }
    }

    @Test
    @DisplayName("Should handle exception during certificate validation")
    void testValidateCertificateException() {
        // Arrange
        when(as4Configuration.isKeystoreConfigured())
            .thenThrow(new RuntimeException("Validation failed"));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.validateCertificate("test-alias");

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    @DisplayName("Should handle keystore load failure in validateCertificate")
    void testValidateCertificateKeystoreLoadFailure() {
        // Arrange
        String alias = "test-alias";
        when(as4Configuration.isKeystoreConfigured()).thenReturn(true);
        when(as4Configuration.getKeystorePath()).thenReturn("/path/to/keystore.jks");

        try (MockedStatic<CertificateUtil> certUtil = mockStatic(CertificateUtil.class)) {
            certUtil.when(() -> CertificateUtil.loadKeyStore(anyString(), anyString(), anyString()))
                .thenReturn(null);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.validateCertificate(alias);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Failed to load keystore", response.getBody().get("error"));
        }
    }
}

