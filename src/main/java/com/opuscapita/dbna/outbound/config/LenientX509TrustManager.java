package com.opuscapita.dbna.outbound.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Custom X509TrustManager for AS4 DBNA messaging
 * <p>
 * This trust manager accepts certificates if they are directly trusted (in the truststore)
 * WITHOUT requiring a complete chain validation. This is necessary for AS4 messaging
 * where intermediate CA certificates might not be available.
 * <p>
 * For server certificate validation, it attempts standard PKIX validation first,
 * but falls back to checking if the certificate is directly in the truststore if PKIX fails.
 * <p>
 * This is appropriate for DBNA AS4 messaging where:
 * 1. The receiver's certificate is obtained from SMP
 * 2. The receiver's certificate is added to the truststore
 * 3. Intermediate CA certificates might not be readily available
 * 4. Direct certificate trust is sufficient for AS4 security model
 */
public class LenientX509TrustManager implements X509TrustManager {
    private static final Logger logger = LoggerFactory.getLogger(LenientX509TrustManager.class);

    private final X509TrustManager standardTrustManager;
    private final KeyStore trustStore;

    public LenientX509TrustManager(X509TrustManager standardTrustManager, KeyStore trustStore) {
        this.standardTrustManager = standardTrustManager;
        this.trustStore = trustStore;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            if (standardTrustManager != null) {
                standardTrustManager.checkClientTrusted(chain, authType);
            }
        } catch (CertificateException e) {
            // Try lenient check
            if (chain != null && chain.length > 0) {
                if (isCertificateInTruststore(chain[0])) {
                    logger.debug("Client certificate accepted (direct trust from truststore): {}",
                        chain[0].getSubjectX500Principal());
                    return;
                }
            }
            throw e;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("Empty certificate chain");
        }

        try {
            // First, try standard PKIX validation
            if (standardTrustManager != null) {
                standardTrustManager.checkServerTrusted(chain, authType);
                logger.debug("Server certificate validated via PKIX: {}",
                    chain[0].getSubjectX500Principal());
                return;
            }
        } catch (CertificateException pkixFailure) {
            // PKIX validation failed - try lenient approach
            logger.debug("PKIX validation failed for: {}, attempting lenient validation",
                chain[0].getSubjectX500Principal());

            // Check if the server certificate is directly trusted (in our truststore)
            if (isCertificateInTruststore(chain[0])) {
                logger.info("Server certificate accepted (direct trust from truststore): {}",
                    chain[0].getSubjectX500Principal());
                return;
            }

            // If no direct trust found, also check if we have the issuer certificate
            // This helps with certificate chain validation
            if (chain.length > 1 && isCertificateInTruststore(chain[1])) {
                logger.info("Server certificate chain validated (issuer in truststore): {} <- {}",
                    chain[0].getSubjectX500Principal(), chain[1].getSubjectX500Principal());
                return;
            }

            // Lenient validation also failed
            logger.error("Server certificate validation failed - not in truststore: {}",
                chain[0].getSubjectX500Principal());
            throw new CertificateException(
                "Server certificate not trusted and PKIX validation failed. Certificate: " +
                chain[0].getSubjectX500Principal() + ", Issuer: " +
                chain[0].getIssuerX500Principal());
        }
    }

    /**
     * Check if a certificate exists in the truststore
     */
    private boolean isCertificateInTruststore(X509Certificate cert) {
        if (trustStore == null || cert == null) {
            return false;
        }

        try {
            // Check by serial number and subject DN combination
            String alias = generateCertificateAlias(cert);
            java.security.cert.Certificate storedCert = trustStore.getCertificate(alias);

            if (storedCert instanceof X509Certificate) {
                X509Certificate storedX509 = (X509Certificate) storedCert;
                // Compare serial numbers and subject DNs
                if (storedX509.getSerialNumber().equals(cert.getSerialNumber()) &&
                    storedX509.getSubjectX500Principal().equals(cert.getSubjectX500Principal())) {
                    logger.debug("Certificate found in truststore: {}", cert.getSubjectX500Principal());
                    return true;
                }
            }

            // Also try direct search by subject DN (some stores use this)
            java.util.Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                String testAlias = aliases.nextElement();
                java.security.cert.Certificate testCert = trustStore.getCertificate(testAlias);
                if (testCert instanceof X509Certificate) {
                    X509Certificate testX509 = (X509Certificate) testCert;
                    if (testX509.getSerialNumber().equals(cert.getSerialNumber()) &&
                        testX509.getSubjectX500Principal().equals(cert.getSubjectX500Principal())) {
                        logger.debug("Certificate found in truststore via enumeration: {}",
                            cert.getSubjectX500Principal());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error checking if certificate is in truststore: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Generate alias compatible with TruststoreManager
     */
    private String generateCertificateAlias(X509Certificate certificate) {
        String subjectDN = certificate.getSubjectX500Principal().getName();
        String serialNumber = certificate.getSerialNumber().toString(16);
        return (subjectDN + "_" + serialNumber).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (standardTrustManager != null) {
            return standardTrustManager.getAcceptedIssuers();
        }
        return new X509Certificate[0];
    }
}

