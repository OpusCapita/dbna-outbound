package com.opuscapita.dbna.common.container.metadata;

import com.google.gson.annotations.Since;
import network.oxalis.api.inbound.InboundMetadata;
import network.oxalis.vefa.peppol.common.model.Header;

import javax.security.auth.x500.X500Principal;
import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Simple POJO representation of eu.peppol.PeppolMessageMetaData for JSON conversion.
 *
 * Populated from the request Header for inbound files
 * Populated from the SBD Header extracted from the payload for outbound files
 */
public class ContainerMessageMetadata implements Serializable {

    private static final long serialVersionUID = 2688223858360763842L;

    public static final String OC_AP_COMMON_NAME = "C=FI, O=OpusCapita Solutions Oy, OU=PEPPOL PRODUCTION AP, CN=PNO000104";

    @Since(1.0) private String messageId;
    @Since(1.0) private String transmissionId;

    @Since(1.0) private String senderId;
    @Since(1.0) private String recipientId;
    @Since(1.0) private String sendingAccessPoint;
    @Since(1.0) private String receivingAccessPoint;
    @Since(1.0) private String documentTypeIdentifier;
    @Since(1.0) private String profileTypeIdentifier;

    @Since(1.0) private String instanceType;
    @Since(1.0) private String instanceTypeVersion;
    @Since(1.0) private String instanceTypeStandard;
    @Since(1.0) private Date timestamp;
    @Since(1.0) private String protocol;
    @Since(1.0) private String userAgent;
    @Since(1.0) private String userAgentVersion;
    @Since(1.0) private ContainerValidationRule validationRule;
    @Since(1.1) private ContainerBusinessMetadata businessMetadata;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getDocumentTypeIdentifier() {
        return documentTypeIdentifier;
    }

    public void setDocumentTypeIdentifier(String documentTypeIdentifier) {
        this.documentTypeIdentifier = documentTypeIdentifier;
    }

    public String getProfileTypeIdentifier() {
        return profileTypeIdentifier;
    }

    public void setProfileTypeIdentifier(String profileTypeIdentifier) {
        this.profileTypeIdentifier = profileTypeIdentifier;
    }

    public String getSendingAccessPoint() {
        return sendingAccessPoint;
    }

    public void setSendingAccessPoint(String sendingAccessPoint) {
        this.sendingAccessPoint = sendingAccessPoint;
    }

    public String getReceivingAccessPoint() {
        return receivingAccessPoint;
    }

    public void setReceivingAccessPoint(String receivingAccessPoint) {
        this.receivingAccessPoint = receivingAccessPoint;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public String getInstanceTypeVersion() {
        return instanceTypeVersion;
    }

    public void setInstanceTypeVersion(String instanceTypeVersion) {
        this.instanceTypeVersion = instanceTypeVersion;
    }

    public String getInstanceTypeStandard() {
        return instanceTypeStandard;
    }

    public void setInstanceTypeStandard(String instanceTypeStandard) {
        this.instanceTypeStandard = instanceTypeStandard;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserAgentVersion() {
        return userAgentVersion;
    }

    public void setUserAgentVersion(String userAgentVersion) {
        this.userAgentVersion = userAgentVersion;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public ContainerValidationRule getValidationRule() {
        return validationRule;
    }

    public void setValidationRule(ContainerValidationRule validationRule) {
        this.validationRule = validationRule;
    }

    public ContainerBusinessMetadata getBusinessMetadata() {
        return businessMetadata;
    }

    public void setBusinessMetadata(ContainerBusinessMetadata businessMetadata) {
        this.businessMetadata = businessMetadata;
    }

    public String getTransmissionId() {
        return transmissionId;
    }

    public void setTransmissionId(String transmissionId) {
        this.transmissionId = transmissionId;
    }

    public static ContainerMessageMetadata create(OcTransmissionResult transmissionResult) {
        Header header = transmissionResult.getHeader();

        ContainerMessageMetadata metadata = new ContainerMessageMetadata();
        metadata.setMessageId(header.getIdentifier().getIdentifier());
        metadata.setTransmissionId(transmissionResult.getTransmissionIdentifier().getIdentifier());
        metadata.setRecipientId(header.getReceiver() != null ? header.getReceiver().getIdentifier() : null);
        metadata.setSenderId(header.getSender() != null ? header.getSender().getIdentifier() : null);
        metadata.setDocumentTypeIdentifier(header.getDocumentType().getIdentifier());
        metadata.setProfileTypeIdentifier(header.getProcess().getIdentifier());
        metadata.setProtocol(transmissionResult.getProtocol().getIdentifier());
        metadata.setTimestamp(transmissionResult.getTimestamp());

        metadata.setInstanceType(header.getInstanceType().getType());
        metadata.setInstanceTypeVersion(header.getInstanceType().getVersion());
        metadata.setInstanceTypeStandard(header.getInstanceType().getStandard());

        return metadata;
    }

    public static ContainerMessageMetadata create(InboundMetadata inboundMetadata) {
        Header header = inboundMetadata.getHeader();
        X509Certificate certificate = inboundMetadata.getCertificate();
        X500Principal principal = certificate.getSubjectX500Principal();

        ContainerMessageMetadata metadata = new ContainerMessageMetadata();
        metadata.setMessageId(header.getIdentifier().getIdentifier());
        metadata.setTransmissionId(inboundMetadata.getTransmissionIdentifier().getIdentifier());
        metadata.setRecipientId(header.getReceiver().getIdentifier());
        metadata.setSenderId(header.getSender().getIdentifier());
        metadata.setDocumentTypeIdentifier(header.getDocumentType().getIdentifier());
        metadata.setProfileTypeIdentifier(header.getProcess().getIdentifier());
        metadata.setSendingAccessPoint(principal.getName());
        metadata.setReceivingAccessPoint(OC_AP_COMMON_NAME);
        metadata.setProtocol(inboundMetadata.getProtocol().getIdentifier());
        metadata.setTimestamp(inboundMetadata.getTimestamp());

        metadata.setInstanceType(header.getInstanceType().getType());
        metadata.setInstanceTypeVersion(header.getInstanceType().getVersion());
        metadata.setInstanceTypeStandard(header.getInstanceType().getStandard());

        return metadata;
    }

    public static ContainerMessageMetadata createDummy() {
        ContainerMessageMetadata metadata = new ContainerMessageMetadata();
        metadata.setMessageId("ff3bb2dc-3ff4-11e6-9605-97ed9690fe22");
        metadata.setTransmissionId("70b6fd04-3ac9-42ea-8d42-86582873453c");
        metadata.setRecipientId("9908:937789416");
        metadata.setSenderId("9908:937270062");
        metadata.setDocumentTypeIdentifier("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:www.cenbii.eu:transaction:biitrns010:ver2.0:extended:urn:www.peppol.eu:bis:peppol5a:ver2.0:extended:urn:www.difi.no:ehf:faktura:ver2.0::2.1");
        metadata.setProfileTypeIdentifier("urn:www.cenbii.eu:profile:bii05:ver2.0");
        metadata.setSendingAccessPoint(OC_AP_COMMON_NAME);
        metadata.setReceivingAccessPoint(OC_AP_COMMON_NAME);
        metadata.setProtocol("AS2");
        metadata.setTimestamp(new Date());
        metadata.setInstanceType("Invoice");
        metadata.setInstanceTypeVersion("2.1");
        metadata.setInstanceTypeStandard("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2");
        return metadata;
    }
}
