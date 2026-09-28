package com.opuscapita.dbna.outbound.service;

import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the issuer chain of a certificate by following the "CA Issuers" entries of its
 * Authority Information Access (AIA) extension (RFC 5280, section 4.2.2.1).
 * Fetched issuer certificates are added to the in-memory truststore so that WSS4J can
 * validate signatures of the receiver's AS4 responses.
 */
@Service
public class CertificateChainBuilder {
    private static final Logger logger = LoggerFactory.getLogger(CertificateChainBuilder.class);

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_CHAIN_DEPTH = 5;

    private final Map<String, List<X509Certificate>> urlCache = new ConcurrentHashMap<>();

    /**
     * Fetch the issuer chain of the given certificate via AIA and add it to the truststore.
     *
     * @return the fetched issuer certificates (may be empty)
     */
    public List<X509Certificate> buildChainFromAIA(X509Certificate certificate, TruststoreManager truststoreManager) {
        List<X509Certificate> chain = new ArrayList<>();
        X509Certificate current = certificate;

        for (int depth = 0; current != null && depth < MAX_CHAIN_DEPTH && !isSelfSigned(current); depth++) {
            X509Certificate issuer = fetchIssuer(current);
            if (issuer == null) {
                logger.warn("Could not obtain issuer certificate '{}' of '{}' via AIA",
                    current.getIssuerX500Principal(), current.getSubjectX500Principal());
                break;
            }
            truststoreManager.addReceiverCertificate(issuer);
            chain.add(issuer);
            logger.info("✓ Added issuer certificate from AIA to truststore: {}", issuer.getSubjectX500Principal());
            current = issuer;
        }
        return chain;
    }

    private X509Certificate fetchIssuer(X509Certificate certificate) {
        for (String url : getCaIssuersUrls(certificate)) {
            for (X509Certificate candidate : fetchCertificates(url)) {
                if (candidate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
                    try {
                        certificate.verify(candidate.getPublicKey());
                        return candidate;
                    } catch (Exception e) {
                        logger.warn("Certificate fetched from {} does not verify '{}': {}",
                            url, certificate.getSubjectX500Principal(), e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private List<String> getCaIssuersUrls(X509Certificate certificate) {
        List<String> urls = new ArrayList<>();
        try {
            AuthorityInformationAccess aia = AuthorityInformationAccess.fromExtensions(
                new JcaX509CertificateHolder(certificate).getExtensions());
            if (aia == null) {
                logger.warn("No AIA extension in certificate: {}", certificate.getSubjectX500Principal());
                return urls;
            }
            for (AccessDescription ad : aia.getAccessDescriptions()) {
                GeneralName location = ad.getAccessLocation();
                if (X509ObjectIdentifiers.id_ad_caIssuers.equals(ad.getAccessMethod())
                        && location.getTagNo() == GeneralName.uniformResourceIdentifier) {
                    String url = location.getName().toString();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        urls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse AIA extension of {}: {}", certificate.getSubjectX500Principal(), e.getMessage());
        }
        return urls;
    }

    private List<X509Certificate> fetchCertificates(String url) {
        List<X509Certificate> cached = urlCache.get(url);
        if (cached != null) {
            return cached;
        }
        List<X509Certificate> result = new ArrayList<>();
        try {
            URLConnection connection = URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            try (InputStream in = connection.getInputStream()) {
                // Handles DER, PEM and PKCS#7 (.p7c) encodings
                Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509").generateCertificates(in);
                for (Certificate c : certs) {
                    if (c instanceof X509Certificate x509) {
                        result.add(x509);
                    }
                }
            }
            logger.info("Fetched {} certificate(s) from AIA URL {}", result.size(), url);
            if (!result.isEmpty()) {
                urlCache.put(url, result);
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch certificate from AIA URL {}: {}", url, e.getMessage());
        }
        return result;
    }

    private static boolean isSelfSigned(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
    }
}

