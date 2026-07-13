package com.opuscapita.dbna.outbound.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Startup event listener that displays loaded certificates for debugging.
 * Shows all certificates loaded in the server keystore at application startup.
 */
@Slf4j
@Component
public class CertificateStartupLogger {

    /**
     * Logs loaded certificates when application is ready.
     * Displays certificate details from server keystore for debugging.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logLoadedCertificates() {
        try {
            log.info("========================================");
            log.info("Loading and displaying server certificates");
            log.info("========================================");

            String keystorePath = System.getProperty("server.ssl.key-store");
            String keystorePassword = System.getProperty("server.ssl.key-store-password");
            String keystoreType = System.getProperty("server.ssl.key-store-type", "PKCS12");

            if (keystorePath == null || keystorePath.isEmpty()) {
                // Try from environment variable
                keystorePath = System.getenv("KEYSTORE_PATH");
                if (keystorePath == null || keystorePath.isEmpty()) {
                    keystorePath = "classpath:certificates/server-keystore.p12";
                }
            }

            if (keystorePassword == null || keystorePassword.isEmpty()) {
                keystorePassword = System.getenv("KEYSTORE_PASSWORD");
                if (keystorePassword == null || keystorePassword.isEmpty()) {
                    keystorePassword = "changeit";
                }
            }

            log.info("Keystore type: {}", keystoreType);
            log.info("Keystore path: {}", maskPath(keystorePath));

            // Load keystore
            KeyStore keyStore = KeyStore.getInstance(keystoreType);

            // Handle classpath resources
            if (keystorePath.startsWith("classpath:")) {
                String resourcePath = keystorePath.substring("classpath:".length());
                var resourceStream = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(resourcePath);
                if (resourceStream != null) {
                    keyStore.load(resourceStream, keystorePassword.toCharArray());
                    log.debug("Loaded keystore from classpath: {}", resourcePath);
                } else {
                    log.warn("Classpath resource not found: {}", resourcePath);
                    return;
                }
            } else {
                // Handle file system paths
                Path keyStorePath = Paths.get(keystorePath);
                if (Files.exists(keyStorePath)) {
                    try (FileInputStream fis = new FileInputStream(keyStorePath.toFile())) {
                        keyStore.load(fis, keystorePassword.toCharArray());
                        log.debug("Loaded keystore from file system: {}", keystorePath);
                    }
                } else {
                    log.warn("Keystore file not found: {}", keystorePath);
                    return;
                }
            }

            // Display all certificates in keystore
            Enumeration<String> aliases = keyStore.aliases();
            int certificateCount = 0;

            log.info("");
            log.info("=== Loaded Certificates ===");

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = keyStore.getCertificate(alias);

                if (cert instanceof X509Certificate) {
                    X509Certificate x509Cert = (X509Certificate) cert;
                    certificateCount++;

                    log.info("");
                    log.info("Certificate #{}: {}", certificateCount, alias);
                    log.info("  Subject DN: {}", x509Cert.getSubjectX500Principal().getName());
                    log.info("  Issuer DN: {}", x509Cert.getIssuerX500Principal().getName());
                    log.info("  Serial Number: {}", x509Cert.getSerialNumber());
                    log.info("  Thumbprint (SHA-1): {}", calculateThumbprint(x509Cert));
                    log.info("  Not Before: {}", x509Cert.getNotBefore());
                    log.info("  Not After: {}", x509Cert.getNotAfter());
                    log.info("  Signature Algorithm: {}", x509Cert.getSigAlgName());
                    log.info("  Public Key Algorithm: {}", x509Cert.getPublicKey().getAlgorithm());
                    log.info("  Certificate Version: X.509 v{}", x509Cert.getVersion());

                    // Check validity
                    try {
                        x509Cert.checkValidity();
                        log.info("  Status: ✓ VALID");
                    } catch (java.security.cert.CertificateExpiredException e) {
                        log.warn("  Status: ✗ EXPIRED");
                    } catch (java.security.cert.CertificateNotYetValidException e) {
                        log.warn("  Status: ✗ NOT YET VALID");
                    }

                    // Log key usage
                    boolean[] keyUsage = x509Cert.getKeyUsage();
                    if (keyUsage != null) {
                        StringBuilder keyUsageStr = new StringBuilder();
                        if (keyUsage.length > 0 && keyUsage[0]) keyUsageStr.append("DigitalSignature ");
                        if (keyUsage.length > 1 && keyUsage[1]) keyUsageStr.append("NonRepudiation ");
                        if (keyUsage.length > 2 && keyUsage[2]) keyUsageStr.append("KeyEncipherment ");
                        if (keyUsage.length > 3 && keyUsage[3]) keyUsageStr.append("DataEncipherment ");
                        if (keyUsage.length > 4 && keyUsage[4]) keyUsageStr.append("KeyAgreement ");
                        if (keyUsage.length > 5 && keyUsage[5]) keyUsageStr.append("KeyCertSign ");
                        if (keyUsage.length > 6 && keyUsage[6]) keyUsageStr.append("CRLSign ");
                        if (keyUsage.length > 7 && keyUsage[7]) keyUsageStr.append("EncipherOnly ");
                        if (keyUsage.length > 8 && keyUsage[8]) keyUsageStr.append("DecipherOnly");
                        if (keyUsageStr.length() > 0) {
                            log.info("  Key Usage: {}", keyUsageStr.toString().trim());
                        }
                    }
                }
            }

            log.info("");
            log.info("Total certificates loaded: {}", certificateCount);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Error loading certificates at startup", e);
        }
    }

    /**
     * Calculates SHA-1 thumbprint of certificate
     */
    private String calculateThumbprint(X509Certificate certificate) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] certEncoded = certificate.getEncoded();
            byte[] digested = digest.digest(certEncoded);
            return bytesToHex(digested);
        } catch (Exception e) {
            log.debug("Failed to calculate thumbprint: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * Converts bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Masks file paths to hide sensitive information in logs
     */
    private String maskPath(String path) {
        if (path == null) return "UNKNOWN";
        if (path.startsWith("classpath:")) {
            return path;
        }
        // Show only filename for security
        return new java.io.File(path).getName();
    }
}

