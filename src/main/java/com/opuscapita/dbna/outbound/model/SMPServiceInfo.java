package com.opuscapita.dbna.outbound.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * DBNA SMP Service Information with endpoint and certificate
 *
 * According to DBNA SMP Profile v1.0:
 * - Each service endpoint includes certificate information (TypeCode: bdxr-as4-signing-encryption)
 * - The endpoint's certificate is the RECEIVER's certificate
 * - The sender uses the receiver's certificate for:
 *   1. Validating that the endpoint is legitimate (before sending)
 *   2. Encrypting the AS4 message for the receiver
 * - The sender does NOT send the receiver's certificate to them
 * - The receiver validates the sender's message signature using the sender's certificate
 *   (obtained by querying SMP for the sender's information)
 */
@Getter
@ToString
public class SMPServiceInfo {

    @Setter
    private String endpointUrl;
    private final String serviceReference;
    private final X509Certificate receiverCertificate;

    public SMPServiceInfo(String endpointUrl, X509Certificate receiverCertificate, String serviceReference) {
        this.endpointUrl = Objects.requireNonNull(endpointUrl, "Endpoint URL is required");
        this.receiverCertificate = receiverCertificate;  // Certificate may be null if not in SMP
        this.serviceReference = serviceReference;  // Service reference may be null if not in SMP
    }

    /**
     * Checks if certificate information is available for pinning
     */
    public boolean hasCertificateInfo() {
        return receiverCertificate != null;
    }
}




