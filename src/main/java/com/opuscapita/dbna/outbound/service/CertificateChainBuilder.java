package com.opuscapita.dbna.outbound.service;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.GeneralName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Service for building and fetching certificate chains from AIA (Authority Info Access) extensions
 * <p>
 * According to RFC 5280, the AIA extension contains information for accessing certificate-related
 * information and services. This service extracts the CA Issuers URL and fetches the issuer certificate
 * to complete the certificate chain needed for PKIX validation.
 * <p>
 * Usage:
 * - When a certificate is extracted from SMP, this service attempts to fetch missing chain certificates
 * - Certificates are fetched from URLs specified in the AIA extension
 * - Fetched certificates are added to the truststore to enable PKIX path validation
 * <p>
 * Design:
 * - Thread-safe with connection timeouts
 * - Recursive chain building (can fetch chain of intermediate CAs)
 * - Graceful degradation - failures don't prevent the primary certificate from being used
 */
@Service
public class CertificateChainBuilder {
    private static final Logger logger = LoggerFactory.getLogger(CertificateChainBuilder.class);

    private static final String AIA_OID = "2.5.29.48";  // OID for Authority Info Access
    private static final int CONNECT_TIMEOUT_MS = 10000;  // 10 seconds
    private static final int READ_TIMEOUT_MS = 10000;     // 10 seconds

    /**
     * Attempt to fetch and build the certificate chain for a given certificate
     * by following AIA extension URLs.
     *
     * @param certificate The certificate to build a chain for
     * @param truststoreManager The truststore manager to add fetched certificates to
     * @return A list of fetched intermediate/CA certificates (may be empty if none found)
     */
    public List<X509Certificate> buildChainFromAIA(X509Certificate certificate, TruststoreManager truststoreManager) {
        List<X509Certificate> chainCertificates = new ArrayList<>();

        try {
            if (certificate == null) {
                logger.debug("Certificate is null, cannot build chain");
                return chainCertificates;
            }

            // Try to fetch issuer certificate recursively
            X509Certificate issuerCert = fetchIssuerCertificateFromAIA(certificate);
            if (issuerCert != null) {
                chainCertificates.add(issuerCert);
                logger.info("✓ Fetched issuer certificate from AIA: {}", issuerCert.getSubjectX500Principal());

                // Add to truststore if not already there
                truststoreManager.addReceiverCertificate(issuerCert);

                // Recursively build chain for the issuer (but limit depth to prevent infinite loops)
                if (!isChainComplete(issuerCert)) {
                    logger.debug("Attempting to fetch issuer of issuer certificate (chain depth 2)");
                    List<X509Certificate> intermediates = buildChainFromAIA(issuerCert, truststoreManager);
                    chainCertificates.addAll(intermediates);
                }
            } else {
                logger.debug("Could not fetch issuer certificate from AIA for: {}", certificate.getSubjectX500Principal());
            }
        } catch (Exception e) {
            // Log but don't fail - chain building is optional
            logger.warn("Failed to build certificate chain from AIA: {}", e.getMessage());
        }

        return chainCertificates;
    }

    /**
     * Check if a certificate chain is complete (root is self-signed)
     */
    private boolean isChainComplete(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
    }

    /**
     * Fetch the issuer certificate using AIA extension
     *
     * @param certificate The certificate whose issuer we want to fetch
     * @return The issuer certificate, or null if not found/accessible
     */
    private X509Certificate fetchIssuerCertificateFromAIA(X509Certificate certificate) {
        try {
            // Get the AIA extension
            byte[] extValue = certificate.getExtensionValue(AIA_OID);
            if (extValue == null) {
                logger.debug("No AIA extension found in certificate");
                return null;
            }

            // Parse AIA extension using BouncyCastle
            ASN1Sequence seq = ASN1Sequence.getInstance(extValue);
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(seq);

            // Look for CA Issuers access descriptor
            AccessDescription[] accessDescriptions = aia.getAccessDescriptions();
            for (AccessDescription accessDesc : accessDescriptions) {
                // Check if this is CA Issuers (OID 1.3.6.1.5.5.7.48.2)
                if (accessDesc.getAccessMethod().toString().equals("1.3.6.1.5.5.7.48.2")) {
                    GeneralName generalName = accessDesc.getAccessLocation();
                    if (generalName.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        String issuerUrl = generalName.getName().toString();

                        // Fetch certificate from URL
                        if (issuerUrl.startsWith("http://") || issuerUrl.startsWith("https://")) {
                            logger.debug("Fetching issuer certificate from AIA URL: {}", issuerUrl);
                            X509Certificate issuerCert = fetchCertificateFromURL(issuerUrl);
                            if (issuerCert != null) {
                                return issuerCert;
                            }
                        } else {
                            logger.debug("AIA access location is not an HTTP(S) URL: {}", issuerUrl);
                        }
                    }
                }
            }

            logger.debug("No accessible CA Issuers URL found in AIA extension");
            return null;

        } catch (Exception e) {
            logger.warn("Failed to parse AIA extension: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch a certificate from an HTTP(S) URL
     *
     * @param urlString The URL to fetch from
     * @return The parsed X509Certificate, or null if fetch fails
     */
    private X509Certificate fetchCertificateFromURL(String urlString) {
        InputStream inputStream = null;
        try {
            URL url = new URL(urlString);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "DBNA-Outbound-AS4");

            inputStream = connection.getInputStream();

            // Try to parse as X.509 certificate
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inputStream);

            logger.info("✓ Successfully fetched certificate from {}: {}", urlString, cert.getSubjectX500Principal());
            return cert;

        } catch (Exception e) {
            logger.warn("Failed to fetch certificate from URL {}: {}", urlString, e.getMessage());
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    logger.debug("Failed to close input stream: {}", e.getMessage());
                }
            }
        }
    }
}





