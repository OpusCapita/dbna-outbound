package com.opuscapita.dbna.outbound.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

/**
 * Utility class for X.509 certificate operations
 */
public class CertificateUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(CertificateUtil.class);

    /**
     * Load and validate a keystore
     * 
     * @param keystorePath Path to the keystore file
     * @param password Keystore password
     * @param type Keystore type (JKS, PKCS12, etc.)
     * @return KeyStore instance if successful, null otherwise
     */
    public static KeyStore loadKeyStore(String keystorePath, String password, String type) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            try (InputStream is = new FileInputStream(keystorePath)) {
                keyStore.load(is, password.toCharArray());
            }
            logger.info("Successfully loaded keystore from: {}", keystorePath);
            return keyStore;
        } catch (Exception e) {
            logger.error("Failed to load keystore from: {}", keystorePath, e);
            return null;
        }
    }

    /**
     * Get certificate information from keystore
     * 
     * @param keyStore The keystore
     * @param alias Certificate alias
     * @return Certificate information string
     */
    public static String getCertificateInfo(KeyStore keyStore, String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;
                StringBuilder info = new StringBuilder();
                info.append("Certificate Information:\n");
                info.append("  Subject: ").append(x509.getSubjectX500Principal().getName()).append("\n");
                info.append("  Issuer: ").append(x509.getIssuerX500Principal().getName()).append("\n");
                info.append("  Serial Number: ").append(x509.getSerialNumber()).append("\n");
                info.append("  Valid From: ").append(x509.getNotBefore()).append("\n");
                info.append("  Valid To: ").append(x509.getNotAfter()).append("\n");
                info.append("  Signature Algorithm: ").append(x509.getSigAlgName()).append("\n");
                return info.toString();
            }
            return "Certificate found but not X.509 format";
        } catch (Exception e) {
            logger.error("Failed to get certificate info for alias: {}", alias, e);
            return "Failed to retrieve certificate information";
        }
    }

    /**
     * Validate certificate expiration
     * 
     * @param keyStore The keystore
     * @param alias Certificate alias
     * @return true if certificate is valid (not expired), false otherwise
     */
    public static boolean validateCertificate(KeyStore keyStore, String alias) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;
                x509.checkValidity();
                logger.info("Certificate '{}' is valid", alias);
                return true;
            }
            logger.warn("Certificate '{}' is not X.509 format", alias);
            return false;
        } catch (Exception e) {
            logger.error("Certificate '{}' validation failed", alias, e);
            return false;
        }
    }

    /**
     * List all aliases in a keystore
     * 
     * @param keyStore The keystore
     * @return String containing all aliases
     */
    public static String listAliases(KeyStore keyStore) {
        try {
            StringBuilder aliases = new StringBuilder("Keystore aliases:\n");
            Enumeration<String> aliasEnum = keyStore.aliases();
            while (aliasEnum.hasMoreElements()) {
                String alias = aliasEnum.nextElement();
                aliases.append("  - ").append(alias);
                if (keyStore.isKeyEntry(alias)) {
                    aliases.append(" (private key)");
                } else if (keyStore.isCertificateEntry(alias)) {
                    aliases.append(" (certificate)");
                }
                aliases.append("\n");
            }
            return aliases.toString();
        } catch (Exception e) {
            logger.error("Failed to list keystore aliases", e);
            return "Failed to list aliases";
        }
    }

    /**
     * Check if a certificate is about to expire
     * 
     * @param keyStore The keystore
     * @param alias Certificate alias
     * @param daysThreshold Number of days threshold
     * @return true if certificate expires within the threshold
     */
    public static boolean isCertificateExpiringSoon(KeyStore keyStore, String alias, int daysThreshold) {
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;
                long millisecondsUntilExpiry = x509.getNotAfter().getTime() - System.currentTimeMillis();
                long daysUntilExpiry = millisecondsUntilExpiry / (1000 * 60 * 60 * 24);
                
                if (daysUntilExpiry <= daysThreshold) {
                    logger.warn("Certificate '{}' expires in {} days", alias, daysUntilExpiry);
                    return true;
                }
                logger.info("Certificate '{}' valid for {} more days", alias, daysUntilExpiry);
                return false;
            }
            return false;
        } catch (Exception e) {
            logger.error("Failed to check certificate expiry for alias: {}", alias, e);
            return false;
        }
    }

    /**
     * Create a dummy self-signed X.509 certificate for testing purposes
     * 
     * @param commonName Common name (CN) for the certificate
     * @param validityDays Number of days the certificate should be valid
     * @return A dummy X509Certificate, or null if creation fails
     */
    public static X509Certificate createDummyCertificate(String commonName, int validityDays) {
        try {
            // Generate RSA key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Set certificate validity period
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + ((long) validityDays * 24 * 60 * 60 * 1000));

            // Create X.500 name (subject and issuer are the same for self-signed)
            X500Name subject = new X500Name("CN=" + commonName + ", O=DBNA Test, C=US");

            // Generate unique serial number
            BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
            
            // Build the certificate
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,                    // Issuer (same as subject for self-signed)
                serialNumber,               // Serial number
                notBefore,                  // Not valid before
                notAfter,                   // Not valid after
                subject,                    // Subject
                keyPair.getPublic()         // Public key
            );

            // Sign the certificate with the private key
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());
            
            // Convert to X509Certificate
            X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer));
            
            logger.info("Successfully created dummy self-signed certificate for CN={}, valid for {} days", 
                commonName, validityDays);
            logger.debug("Certificate serial number: {}", serialNumber);
            
            return cert;
            
        } catch (Exception e) {
            logger.error("Failed to create dummy certificate for CN={}", commonName, e);
            return null;
        }
    }

    /**
     * Create a dummy self-signed X.509 certificate with default parameters
     * Uses "CN=Test Certificate" as common name and 365 days validity
     * 
     * @return A dummy X509Certificate, or null if creation fails
     */
    public static X509Certificate createDummyCertificate() {
        return createDummyCertificate("Test Certificate", 365);
    }
}

