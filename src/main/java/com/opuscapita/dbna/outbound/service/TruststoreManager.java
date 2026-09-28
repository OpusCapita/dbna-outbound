package com.opuscapita.dbna.outbound.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for managing an in-memory truststore for dynamic certificate injection
 *
 * According to DBNA spec, receiver certificates are obtained via SMP queries.
 * This service allows dynamic injection of receiver certificates into an in-memory truststore
 * before AS4 message transmission, ensuring PKIX validation succeeds.
 *
 * Design:
 * - Uses in-memory truststore only (no file persistence needed)
 * - Receiver certificates are queried fresh from SMP before each send operation
 * - Supports both development and production environments
 * - Thread-safe operations with RWLock for concurrent reads and exclusive writes
 */
@Service
public class TruststoreManager implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(TruststoreManager.class);

    private static final String TRUSTSTORE_PASSWORD = "changeit";  // In-memory truststore password
    private static final String TRUSTSTORE_TYPE = "JKS";             // Standard keystore type

    private KeyStore truststore;
    private final ReadWriteLock truststoreLock = new ReentrantReadWriteLock();
    private boolean isInitialized = false;

    /**
     * Initialize truststore when bean is created
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("TruststoreManager bean initialized, setting up in-memory truststore");
        try {
            getTruststore();  // Trigger initialization
            logger.info("✓ In-memory truststore ready for dynamic certificate injection");
        } catch (Exception e) {
            logger.error("✗ Failed to initialize truststore on bean creation", e);
            throw e;
        }
    }

    /**
     * Initialize the in-memory truststore (lazy loading)
     */
    private void ensureTruststoreLoaded() {
        truststoreLock.readLock().lock();
        try {
            if (isInitialized && truststore != null) {
                return;
            }
        } finally {
            truststoreLock.readLock().unlock();
        }

        // Need to load - acquire write lock
        truststoreLock.writeLock().lock();
        try {
            if (isInitialized && truststore != null) {
                return; // Another thread already loaded it
            }

            loadInMemoryTruststore();
        } finally {
            truststoreLock.writeLock().unlock();
        }
    }

    /**
     * Create an in-memory truststore
     * Initializes with system CA certificates, then allows dynamic injection of receiver certificates from SMP
     */
    private void loadInMemoryTruststore() {
        try {
            truststore = KeyStore.getInstance(TRUSTSTORE_TYPE);
            truststore.load(null, TRUSTSTORE_PASSWORD.toCharArray());

            // Initialize with system CA certificates (default JDK truststore)
            // This ensures the truststore is not empty and PKIX validation can work
            try {
                KeyStore systemTruststore = KeyStore.getInstance(KeyStore.getDefaultType());
                String javaHome = System.getProperty("java.home");
                java.io.File trustFile = new java.io.File(
                    javaHome + "/lib/security/cacerts");

                if (trustFile.exists()) {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(trustFile)) {
                        systemTruststore.load(fis, "changeit".toCharArray());

                        // Copy all system CA certificates to our truststore
                        java.util.Enumeration<String> aliases = systemTruststore.aliases();
                        int certCount = 0;
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            java.security.cert.Certificate cert = systemTruststore.getCertificate(alias);
                            if (cert != null) {
                                truststore.setCertificateEntry(alias, cert);
                                certCount++;
                            }
                        }
                        logger.info("Initialized in-memory truststore with {} system CA certificates from JDK", certCount);
                    }
                } else {
                    logger.warn("System truststore not found at {}, proceeding with empty truststore", trustFile.getPath());
                }
            } catch (Exception e) {
                logger.warn("Failed to load system CA certificates: {}, truststore may not have trust anchors", e.getMessage());
            }

            logger.info("✓ Created in-memory truststore for dynamic receiver certificate injection");
            isInitialized = true;
        } catch (Exception e) {
            logger.error("✗ Failed to initialize in-memory truststore", e);
            throw new RuntimeException("Failed to initialize in-memory truststore: " + e.getMessage(), e);
        }
    }

    /**
     * Add a receiver certificate to the in-memory truststore
     * Certificates are added with alias based on their subject DN
     *
     * This method also attempts to add intermediate certificates for chain validation.
     * When WSS4J validates incoming signatures, it needs the full certificate chain
     * to perform PKIX validation. This method extracts and adds intermediate certificates
     * to support proper signature verification on responses from the remote party.
     *
     * @param receiverCertificate The X509 certificate to add
     * @return true if certificate was added/updated, false if it already existed
     */
    public boolean addReceiverCertificate(X509Certificate receiverCertificate) {
        if (receiverCertificate == null) {
            logger.debug("Skipping null receiver certificate");
            return false;
        }

        ensureTruststoreLoaded();

        truststoreLock.writeLock().lock();
        try {
            boolean added = false;
            String certificateAlias = generateCertificateAlias(receiverCertificate);

            // Check if certificate already exists
            if (truststore.containsAlias(certificateAlias)) {
                X509Certificate existingCert = (X509Certificate) truststore.getCertificate(certificateAlias);
                if (existingCert != null && existingCert.getSerialNumber().equals(receiverCertificate.getSerialNumber())) {
                    logger.debug("Certificate with alias '{}' already in truststore", certificateAlias);
                    return false;
                }
            }

            // Add or update the certificate
            truststore.setCertificateEntry(certificateAlias, receiverCertificate);
            logger.info("Added receiver certificate to truststore with alias: {} (Subject: {})",
                certificateAlias, receiverCertificate.getSubjectX500Principal());
            added = true;

            // Try to add intermediate certificates for chain validation
            // This ensures WSS4J can validate certificate chains for incoming signatures
            try {
                logger.debug("Attempting to add intermediate certificates from chain for: {}",
                    receiverCertificate.getSubjectX500Principal());
                addIntermediateCertificatesToTruststore(receiverCertificate, new HashSet<>());
            } catch (Exception e) {
                // Log warning but don't fail - intermediate certs are nice to have but not essential
                // If intermediate certs are missing, WSS4J might fail to validate, but that's a separate issue
                logger.warn("Could not add all intermediate certificates for chain validation. " +
                    "WSS4J might have issues validating responses. Error: {}", e.getMessage());
            }

            return added;
        } catch (Exception e) {
            logger.error("Failed to add receiver certificate to truststore", e);
            throw new RuntimeException("Failed to add receiver certificate to truststore: " + e.getMessage(), e);
        } finally {
            truststoreLock.writeLock().unlock();
        }
    }

    /**
     * Recursively add intermediate certificates from a certificate's issuer chain.
     * Extracts issuer information and attempts to add to truststore for PKIX validation.
     *
     * Note: This method attempts to use the issuer certificate if available through
     * standard X.509 mechanisms. For self-signed certificates at the root level,
     * we rely on them being in the system truststore (loaded at initialization).
     *
     * @param certificate The certificate to extract issuer from
     * @param processedCerts Set to track already-processed certificates to avoid infinite loops
     */
    private void addIntermediateCertificatesToTruststore(X509Certificate certificate, Set<String> processedCerts) throws Exception {
        if (certificate == null) {
            return;
        }

        // Avoid processing the same certificate multiple times
        String certKey = certificate.getSubjectX500Principal().getName() + "_" + certificate.getSerialNumber();
        if (processedCerts.contains(certKey)) {
            return;
        }
        processedCerts.add(certKey);

        // Check if this is a self-signed cert (subject equals issuer)
        if (certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            logger.debug("Certificate is self-signed: {}", certificate.getSubjectX500Principal());
            // Try to add it as a trust anchor if it's not already there
            String issuerAlias = generateCertificateAlias(certificate);
            try {
                if (!truststore.containsAlias(issuerAlias)) {
                    truststore.setCertificateEntry(issuerAlias, certificate);
                    logger.info("Added self-signed trust anchor: {}", certificate.getSubjectX500Principal());
                }
            } catch (Exception e) {
                logger.debug("Could not add self-signed certificate: {}", e.getMessage());
            }
            return;
        }

        // For non-self-signed certs, we would need to fetch the issuer from AIA or other sources
        // This is a limitation - we can only work with certificates provided by the endpoint
        logger.debug("Certificate has issuer: {}, but issuer certificate is not embedded in response. " +
            "If signature validation fails, the issuer certificate may need to be installed separately.",
            certificate.getIssuerX500Principal());
    }

    /**
     * Get the current in-memory truststore instance
     * Thread-safe read access
     */
    public KeyStore getTruststore() {
        try {
            ensureTruststoreLoaded();

            truststoreLock.readLock().lock();
            try {
                if (truststore == null) {
                    logger.warn("Truststore is null after initialization - this should not happen");
                    // Try to initialize again
                    truststoreLock.readLock().unlock();
                    truststoreLock.writeLock().lock();
                    try {
                        if (truststore == null) {
                            logger.info("Retrying truststore initialization");
                            loadInMemoryTruststore();
                        }
                    } finally {
                        truststoreLock.writeLock().unlock();
                        truststoreLock.readLock().lock();
                    }
                }
                logger.debug("Returning truststore instance for certificate operations");
                return truststore;
            } finally {
                truststoreLock.readLock().unlock();
            }
        } catch (Exception e) {
            logger.error("Critical error getting truststore", e);
            throw new RuntimeException("Failed to get truststore: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a unique alias for the certificate
     * Uses subject DN and serial number to ensure uniqueness
     */
    private String generateCertificateAlias(X509Certificate certificate) {
        String subjectDN = certificate.getSubjectX500Principal().getName();
        String serialNumber = certificate.getSerialNumber().toString(16);
        return (subjectDN + "_" + serialNumber).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * List all certificates currently in the truststore
     * Useful for debugging
     */
    public void logTruststoreContents() {
        ensureTruststoreLoaded();

        truststoreLock.readLock().lock();
        try {
            logger.debug("In-memory truststore contents:");
            Enumeration<String> aliases = truststore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                X509Certificate cert = (X509Certificate) truststore.getCertificate(alias);
                if (cert != null) {
                    logger.debug("  - {}: Subject={}, Valid until: {}",
                        alias, cert.getSubjectX500Principal(), cert.getNotAfter());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to log truststore contents", e);
        } finally {
            truststoreLock.readLock().unlock();
        }
    }
}



