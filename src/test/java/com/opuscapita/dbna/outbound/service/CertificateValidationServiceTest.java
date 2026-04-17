package com.opuscapita.dbna.outbound.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CertificateValidationService
 * Tests X.509 certificate validation for DBNA network compliance
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateValidationService Unit Tests")
class CertificateValidationServiceTest {

    @Mock(lenient = true)
    private X509Certificate certificate;

    private CertificateValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new CertificateValidationService();
    }

    @Test
    @DisplayName("Should reject null certificate chain")
    void testNullCertificateChainThrowsException() {
        // Arrange
        X509Certificate[] nullChain = null;

        // Act & Assert
        assertThrows(CertificateException.class, () ->
            validationService.validateCertificateChain(nullChain, "RSA")
        );
    }

    @Test
    @DisplayName("Should reject empty certificate chain")
    void testEmptyCertificateChainThrowsException() {
        // Arrange
        X509Certificate[] emptyChain = new X509Certificate[0];

        // Act & Assert
        assertThrows(CertificateException.class, () ->
            validationService.validateCertificateChain(emptyChain, "RSA")
        );
    }

    @Test
    @DisplayName("Should validate certificate not expired")
    void testValidateNotExpiredCertificate() throws CertificateParsingException {
        // Arrange - Certificate not expired
        Date futureDate = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);
        when(certificate.getNotAfter()).thenReturn(futureDate);
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=DBNA,C=US"
            ));
        when(certificate.getExtendedKeyUsage())
            .thenReturn(List.of("1.3.6.1.5.5.7.3.1"));

        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert
        assertTrue(result.notExpired);
    }

    @Test
    @DisplayName("Should detect expired certificate")
    void testDetectExpiredCertificate() throws CertificateParsingException {
        // Arrange - Certificate expired
        Date pastDate = new Date(System.currentTimeMillis() - 1000); // 1 second ago
        when(certificate.getNotAfter()).thenReturn(pastDate);
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=DBNA,C=US"
            ));

        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert
        assertFalse(result.notExpired);
        assertNotNull(result.expirationError);
    }

    @Test
    @DisplayName("Should warn when certificate expires soon")
    void testWarnWhenExpiringSoon() throws CertificateParsingException {
        // Arrange - Certificate expires in 15 days
        long expiryMs = System.currentTimeMillis() + (15L * 24 * 60 * 60 * 1000);
        Date soonDate = new Date(expiryMs);
        when(certificate.getNotAfter()).thenReturn(soonDate);
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=DBNA,C=US"
            ));

        // Set warning threshold to 30 days
        ReflectionTestUtils.setField(validationService, "warnDaysBeforeExpiry", 30);
        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert - Should not fail (warning only)
        assertTrue(result.notExpired);
    }

    @Test
    @DisplayName("Should validate subject DN contains DBNA organization")
    void testValidateDBNAOrganizationInSubject() {
        // Arrange
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=Digital Business Networks Alliance,C=US"
            ));

        // Act
        boolean result = validationService.validateSubjectDN(certificate);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should accept certificates without DBNA organization (warning only)")
    void testNonDBNACertificateWarning() {
        // Arrange
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=Other Organization,C=US"
            ));

        // Act
        boolean result = validationService.validateSubjectDN(certificate);

        // Assert - Should not fail (warning only)
        assertTrue(result);
    }

    @Test
    @DisplayName("Should validate issuer DN")
    void testValidateIssuerDN() {
        // Arrange
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=Digital Business Networks Alliance,C=US"
            ));

        // Act
        boolean result = validationService.validateIssuerDN(certificate);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should validate Server Authentication Extended Key Usage")
    void testValidateServerAuthenticationOID() throws CertificateParsingException {
        // Arrange - Mock certificate with Server Authentication OID
        when(certificate.getExtendedKeyUsage())
            .thenReturn(List.of("1.3.6.1.5.5.7.3.1"));  // Server Auth OID

        // Act
        boolean result = validationService.validateExtendedKeyUsage(certificate);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject certificate without Server Authentication OID")
    void testRejectCertificateWithoutServerAuthOID() throws CertificateParsingException {
        // Arrange - Certificate without Server Authentication
        when(certificate.getExtendedKeyUsage())
            .thenReturn(List.of("1.3.6.1.5.5.7.3.2"));  // Code Signing OID

        // Act
        boolean result = validationService.validateExtendedKeyUsage(certificate);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Should handle certificate with no extended key usage")
    void testCertificateWithNoExtendedKeyUsage() throws CertificateParsingException {
        // Arrange
        when(certificate.getExtendedKeyUsage()).thenReturn(null);

        // Act
        boolean result = validationService.validateExtendedKeyUsage(certificate);

        // Assert - Should be lenient
        assertTrue(result);
    }

    @Test
    @DisplayName("Should perform complete DBNA validation")
    void testCompleteDBNAValidation() throws CertificateParsingException {
        // Arrange
        when(certificate.getNotAfter())
            .thenReturn(new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L));
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=DBNA,C=US"
            ));
        when(certificate.getExtendedKeyUsage())
            .thenReturn(List.of("1.3.6.1.5.5.7.3.1"));

        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert
        assertTrue(result.valid);
        assertTrue(result.subjectValid);
        assertTrue(result.issuerValid);
        assertTrue(result.extendedKeyUsageValid);
        assertTrue(result.notExpired);
    }

    @Test
    @DisplayName("Should identify expired certificate in DBNA validation")
    void testDBNAValidationDetectsExpired() {
        // Arrange
        when(certificate.getNotAfter())
            .thenReturn(new Date(System.currentTimeMillis() - 1000));  // Expired
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=DBNA,C=US"
            ));

        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert
        assertFalse(result.valid);
        assertFalse(result.notExpired);
        assertNotNull(result.expirationError);
    }

    @Test
    @DisplayName("Should validate result to string conversion")
    void testValidationResultToString() {
        // Arrange
        CertificateValidationService.CertificateValidationResult result = 
            new CertificateValidationService.CertificateValidationResult();
        result.valid = true;
        result.subjectValid = true;
        result.issuerValid = true;
        result.notExpired = true;

        // Act
        String resultString = result.toString();

        // Assert
        assertNotNull(resultString);
        assertTrue(resultString.contains("valid=true"));
    }

    @Test
    @DisplayName("Should handle certificate exception during DBNA validation")
    void testHandleCertificateExceptionInDBNAValidation() throws CertificateParsingException {
        // Arrange - Cause exception during validation
        when(certificate.getNotAfter())
            .thenThrow(new RuntimeException("Certificate error"));
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));

        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert - Should handle gracefully
        assertFalse(result.valid);
        assertNotNull(result.error);
    }

    @Test
    @DisplayName("Should support disabling expiration checks")
    void testDisableExpirationChecks() throws CertificateParsingException {
        // Arrange
        ReflectionTestUtils.setField(validationService, "checkExpiration", false);
        
        when(certificate.getNotAfter())
            .thenReturn(new Date(System.currentTimeMillis() - 1000));  // Would be expired

        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=example.com,O=DBNA,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA CA,O=DBNA,C=US"
            ));

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert - Should not check expiration
        assertTrue(result.valid);
    }

    @Test
    @DisplayName("Should configure warning days before expiry")
    void testConfigureWarningDays() {
        // Arrange
        ReflectionTestUtils.setField(validationService, "warnDaysBeforeExpiry", 60);

        // Act
        Object warnDays = ReflectionTestUtils.getField(validationService, "warnDaysBeforeExpiry");

        // Assert
        assertEquals(60, warnDays);
    }

    @Test
    @DisplayName("Certificate with all validations passing should be accepted")
    void testFullyValidCertificateAccepted() throws CertificateParsingException {
        // Arrange - Certificate meets all DBNA requirements
        when(certificate.getNotAfter())
            .thenReturn(new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L));
        when(certificate.getSubjectX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=as4.example.com,O=Digital Business Networks Alliance,C=US"
            ));
        when(certificate.getIssuerX500Principal())
            .thenReturn(new javax.security.auth.x500.X500Principal(
                "CN=DBNA Root CA,O=Digital Business Networks Alliance,C=US"
            ));
        when(certificate.getExtendedKeyUsage())
            .thenReturn(List.of("1.3.6.1.5.5.7.3.1"));

        ReflectionTestUtils.setField(validationService, "checkExpiration", true);

        // Act
        CertificateValidationService.CertificateValidationResult result = 
            validationService.validateForDBNA(certificate);

        // Assert
        assertTrue(result.valid);
        assertTrue(result.notExpired);
        assertTrue(result.extendedKeyUsageValid);
    }
}














