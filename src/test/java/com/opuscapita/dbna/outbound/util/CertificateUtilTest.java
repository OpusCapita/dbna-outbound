package com.opuscapita.dbna.outbound.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CertificateUtil
 */
class CertificateUtilTest {

    private static final String KEYSTORE_PASSWORD = "testpassword";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String TEST_KEYSTORE_PATH = "/keystores/test-keystore.p12";
    
    private static final String ALIAS_TEST_CERT = "testcert";
    private static final String ALIAS_EXPIRING_CERT = "expiringcert";
    private static final String ALIAS_CERT_ONLY = "certonly";
    private static final String ALIAS_NON_EXISTENT = "nonexistent";

    private static KeyStore testKeyStore;
    private static String keystorePath;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws Exception {
        // Copy the test keystore from resources to a temporary file
        // This is necessary because loadKeyStore expects a file path
        File tempKeystore = tempDir.resolve("test-keystore.p12").toFile();
        
        try (InputStream is = CertificateUtilTest.class.getResourceAsStream(TEST_KEYSTORE_PATH);
             FileOutputStream fos = new FileOutputStream(tempKeystore)) {
            if (is == null) {
                throw new IllegalStateException("Test keystore not found in resources: " + TEST_KEYSTORE_PATH);
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        
        keystorePath = tempKeystore.getAbsolutePath();
        testKeyStore = CertificateUtil.loadKeyStore(keystorePath, KEYSTORE_PASSWORD, KEYSTORE_TYPE);
        assertNotNull(testKeyStore, "Test keystore should be loaded successfully");
    }

    @Test
    void testLoadKeyStore_Success() {
        KeyStore keyStore = CertificateUtil.loadKeyStore(keystorePath, KEYSTORE_PASSWORD, KEYSTORE_TYPE);
        
        assertNotNull(keyStore);
        assertDoesNotThrow(() -> {
            assertTrue(keyStore.containsAlias(ALIAS_TEST_CERT));
            assertTrue(keyStore.containsAlias(ALIAS_EXPIRING_CERT));
            assertTrue(keyStore.containsAlias(ALIAS_CERT_ONLY));
        });
    }

    @Test
    void testLoadKeyStore_InvalidPassword() {
        KeyStore keyStore = CertificateUtil.loadKeyStore(keystorePath, "wrongpassword", KEYSTORE_TYPE);
        
        assertNull(keyStore, "Loading keystore with wrong password should return null");
    }

    @Test
    void testLoadKeyStore_InvalidPath() {
        KeyStore keyStore = CertificateUtil.loadKeyStore("/nonexistent/path/keystore.p12", 
                                                         KEYSTORE_PASSWORD, KEYSTORE_TYPE);
        
        assertNull(keyStore, "Loading keystore from invalid path should return null");
    }

    @Test
    void testLoadKeyStore_InvalidType() {
        KeyStore keyStore = CertificateUtil.loadKeyStore(keystorePath, KEYSTORE_PASSWORD, "INVALID_TYPE");
        
        assertNull(keyStore, "Loading keystore with invalid type should return null");
    }

    @Test
    void testGetCertificateInfo_ValidCertificate() {
        String info = CertificateUtil.getCertificateInfo(testKeyStore, ALIAS_TEST_CERT);
        
        assertNotNull(info);
        assertTrue(info.contains("Certificate Information:"));
        assertTrue(info.contains("Subject:"));
        assertTrue(info.contains("CN=Test Certificate"));
        assertTrue(info.contains("O=DBNA Test"));
        assertTrue(info.contains("C=US"));
        assertTrue(info.contains("Issuer:"));
        assertTrue(info.contains("Serial Number:"));
        assertTrue(info.contains("Valid From:"));
        assertTrue(info.contains("Valid To:"));
        assertTrue(info.contains("Signature Algorithm:"));
    }

    @Test
    void testGetCertificateInfo_ExpiringCertificate() {
        String info = CertificateUtil.getCertificateInfo(testKeyStore, ALIAS_EXPIRING_CERT);
        
        assertNotNull(info);
        assertTrue(info.contains("Certificate Information:"));
        assertTrue(info.contains("CN=Expiring Certificate"));
    }

    @Test
    void testGetCertificateInfo_NonExistentAlias() {
        String info = CertificateUtil.getCertificateInfo(testKeyStore, ALIAS_NON_EXISTENT);
        
        assertNotNull(info);
        assertTrue(info.contains("Certificate found but not X.509 format") || 
                   info.contains("Failed to retrieve certificate information"),
                   "Non-existent alias should return appropriate message");
    }

    @Test
    void testValidateCertificate_ValidCertificate() {
        boolean isValid = CertificateUtil.validateCertificate(testKeyStore, ALIAS_TEST_CERT);
        
        assertTrue(isValid, "Valid certificate should pass validation");
    }

    @Test
    void testValidateCertificate_ExpiringCertificate() {
        // This certificate is valid for 15 days, so it should still be valid
        boolean isValid = CertificateUtil.validateCertificate(testKeyStore, ALIAS_EXPIRING_CERT);
        
        assertTrue(isValid, "Certificate valid for 15 days should pass validation");
    }

    @Test
    void testValidateCertificate_NonExistentAlias() {
        boolean isValid = CertificateUtil.validateCertificate(testKeyStore, ALIAS_NON_EXISTENT);
        
        assertFalse(isValid, "Non-existent certificate should fail validation");
    }

    @Test
    void testListAliases() {
        String aliases = CertificateUtil.listAliases(testKeyStore);
        
        assertNotNull(aliases);
        assertTrue(aliases.contains("Keystore aliases:"));
        assertTrue(aliases.contains(ALIAS_TEST_CERT));
        assertTrue(aliases.contains(ALIAS_EXPIRING_CERT));
        assertTrue(aliases.contains(ALIAS_CERT_ONLY));
        assertTrue(aliases.contains("(private key)"));
        assertTrue(aliases.contains("(certificate)"));
    }

    @Test
    void testListAliases_DistinguishesKeyTypes() {
        String aliases = CertificateUtil.listAliases(testKeyStore);
        
        // testcert and expiringcert should be marked as private key entries
        assertTrue(aliases.matches("(?s).*" + ALIAS_TEST_CERT + ".*\\(private key\\).*"));
        assertTrue(aliases.matches("(?s).*" + ALIAS_EXPIRING_CERT + ".*\\(private key\\).*"));
        
        // certonly should be marked as certificate entry
        assertTrue(aliases.matches("(?s).*" + ALIAS_CERT_ONLY + ".*\\(certificate\\).*"));
    }

    @Test
    void testIsCertificateExpiringSoon_NotExpiring() {
        // testcert is valid for 3650 days (10 years)
        boolean expiringSoon = CertificateUtil.isCertificateExpiringSoon(testKeyStore, ALIAS_TEST_CERT, 30);
        
        assertFalse(expiringSoon, "Certificate valid for 10 years should not be expiring soon");
    }

    @Test
    void testIsCertificateExpiringSoon_Expiring() {
        // expiringcert is valid for only 15 days
        boolean expiringSoon = CertificateUtil.isCertificateExpiringSoon(testKeyStore, ALIAS_EXPIRING_CERT, 30);
        
        assertTrue(expiringSoon, "Certificate valid for 15 days should be expiring within 30 days");
    }

    @Test
    void testIsCertificateExpiringSoon_ExactThreshold() {
        // Test with threshold that should trigger warning
        boolean expiringSoon = CertificateUtil.isCertificateExpiringSoon(testKeyStore, ALIAS_EXPIRING_CERT, 15);
        
        assertTrue(expiringSoon, "Certificate expiring in exactly 15 days should trigger 15-day threshold");
    }

    @Test
    void testIsCertificateExpiringSoon_NonExistentAlias() {
        boolean expiringSoon = CertificateUtil.isCertificateExpiringSoon(testKeyStore, ALIAS_NON_EXISTENT, 30);
        
        assertFalse(expiringSoon, "Non-existent certificate should return false");
    }

    @Test
    void testCreateDummyCertificate_WithParameters() {
        String commonName = "Test Dummy Certificate";
        int validityDays = 365;
        
        X509Certificate cert = CertificateUtil.createDummyCertificate(commonName, validityDays);
        
        assertNotNull(cert, "Dummy certificate should be created successfully");
        assertTrue(cert.getSubjectX500Principal().getName().contains("CN=" + commonName));
        assertTrue(cert.getSubjectX500Principal().getName().contains("O=DBNA Test"));
        assertTrue(cert.getSubjectX500Principal().getName().contains("C=US"));
        
        // Verify it's self-signed (issuer = subject)
        assertEquals(cert.getSubjectX500Principal().getName(), 
                     cert.getIssuerX500Principal().getName(),
                     "Self-signed certificate should have same issuer and subject");
        
        // Verify validity period
        assertNotNull(cert.getNotBefore());
        assertNotNull(cert.getNotAfter());
        
        long validityMillis = cert.getNotAfter().getTime() - cert.getNotBefore().getTime();
        long expectedMillis = (long) validityDays * 24 * 60 * 60 * 1000;
        
        // Allow for small timing differences (within 1 day)
        assertTrue(Math.abs(validityMillis - expectedMillis) < 24 * 60 * 60 * 1000,
                   "Certificate validity period should match requested days");
    }

    @Test
    void testCreateDummyCertificate_DefaultParameters() {
        X509Certificate cert = CertificateUtil.createDummyCertificate();
        
        assertNotNull(cert, "Dummy certificate with defaults should be created successfully");
        assertTrue(cert.getSubjectX500Principal().getName().contains("CN=Test Certificate"));
        
        // Verify default validity is approximately 365 days
        long validityMillis = cert.getNotAfter().getTime() - cert.getNotBefore().getTime();
        long expectedMillis = 365L * 24 * 60 * 60 * 1000;
        
        // Allow for small timing differences (within 1 day)
        assertTrue(Math.abs(validityMillis - expectedMillis) < 24 * 60 * 60 * 1000,
                   "Default certificate validity should be approximately 365 days");
    }

    @Test
    void testCreateDummyCertificate_DifferentCommonNames() {
        String cn1 = "Certificate One";
        String cn2 = "Certificate Two";
        
        X509Certificate cert1 = CertificateUtil.createDummyCertificate(cn1, 100);
        X509Certificate cert2 = CertificateUtil.createDummyCertificate(cn2, 200);
        
        assertNotNull(cert1);
        assertNotNull(cert2);
        
        assertTrue(cert1.getSubjectX500Principal().getName().contains("CN=" + cn1));
        assertTrue(cert2.getSubjectX500Principal().getName().contains("CN=" + cn2));
        
        // Verify they have different serial numbers
        assertNotEquals(cert1.getSerialNumber(), cert2.getSerialNumber(),
                        "Different certificates should have different serial numbers");
    }

    @Test
    void testCreateDummyCertificate_CanBeValidated() {
        X509Certificate cert = CertificateUtil.createDummyCertificate("Valid Test Cert", 365);
        
        assertNotNull(cert);
        
        // Certificate should be currently valid
        assertDoesNotThrow(() -> cert.checkValidity(),
                          "Newly created certificate should be currently valid");
    }

    @Test
    void testCreateDummyCertificate_ShortValidity() {
        X509Certificate cert = CertificateUtil.createDummyCertificate("Short Validity", 1);
        
        assertNotNull(cert);
        
        long validityMillis = cert.getNotAfter().getTime() - cert.getNotBefore().getTime();
        long expectedMillis = 24L * 60 * 60 * 1000; // 1 day
        
        // Allow for small timing differences (within 1 hour)
        assertTrue(Math.abs(validityMillis - expectedMillis) < 60 * 60 * 1000,
                   "Certificate with 1-day validity should be created correctly");
    }

    @Test
    void testCreateDummyCertificate_LongValidity() {
        X509Certificate cert = CertificateUtil.createDummyCertificate("Long Validity", 3650);
        
        assertNotNull(cert);
        
        long validityMillis = cert.getNotAfter().getTime() - cert.getNotBefore().getTime();
        long expectedMillis = 3650L * 24 * 60 * 60 * 1000; // 10 years
        
        // Allow for small timing differences (within 1 day)
        assertTrue(Math.abs(validityMillis - expectedMillis) < 24 * 60 * 60 * 1000,
                   "Certificate with 10-year validity should be created correctly");
    }
}



