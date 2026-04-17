package com.opuscapita.dbna.common.container.metadata;

import network.oxalis.api.model.TransmissionIdentifier;
import network.oxalis.api.transmission.TransmissionResult;
import network.oxalis.vefa.peppol.common.model.Digest;
import network.oxalis.vefa.peppol.common.model.DocumentTypeIdentifier;
import network.oxalis.vefa.peppol.common.model.Header;
import network.oxalis.vefa.peppol.common.model.Receipt;
import network.oxalis.vefa.peppol.common.model.TransportProfile;
import network.oxalis.vefa.peppol.common.model.TransportProtocol;

import java.util.Date;
import java.util.List;

public class OcTransmissionResult implements TransmissionResult {

    private final TransmissionIdentifier transmissionIdentifier;
    private final Header header;
    private final Date timestamp;
    private final TransportProfile transportProfile;

    public OcTransmissionResult(Header header) {
        this.header = header;
        this.timestamp = new Date();
        this.transportProfile = TransportProfile.of(DocumentTypeIdentifier.DEFAULT_SCHEME.getIdentifier());
        this.transmissionIdentifier = TransmissionIdentifier.generateUUID();
    }

    public TransmissionIdentifier getTransmissionIdentifier() {
        return transmissionIdentifier;
    }

    @Override
    public Header getHeader() {
        return header;
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    @Override
    public TransportProfile getProtocol() {
        return transportProfile;
    }

    @Override
    public TransportProtocol getTransportProtocol() {
        return TransportProtocol.AS2;
    }

    @Override
    public Digest getDigest() {
        return null;
    }

    @Override
    public List<Receipt> getReceipts() {
        return null;
    }

    @Override
    public Receipt primaryReceipt() {
        return null;
    }
}

