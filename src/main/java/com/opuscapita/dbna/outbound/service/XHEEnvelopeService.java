package com.opuscapita.dbna.outbound.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Service for wrapping UBL documents in XHE (eXtensible Header Envelope) format
 * per DBNA Alliance XHE Profile v1.0
 * <p>
 * Implementation approach:
 * - Uses DOM manipulation for direct control over XML structure and namespaces
 * - Avoids dependency on JAXB-based libraries (ph-xhe has compatibility issues)
 * - DOM provides the most flexible and compatible way to create valid XHE envelopes
 * - Complies with OASIS BDXR XHE standard and DBNA validation rules
 * <p>
 * Reference:
 * - OASIS BDXR XHE: <a href="https://docs.oasis-open.org/bdxr/xs/v1.0/os/">https://docs.oasis-open.org/bdxr/xs/v1.0/os/</a>
 * - DBNA XHE Profile: Per DBNA_XHE.sch validation rules
 */
@Service
public class XHEEnvelopeService {
    private static final Logger logger = LoggerFactory.getLogger(XHEEnvelopeService.class);

    // XHE namespace constants per OASIS BDXR specification
    private static final String XHE_NAMESPACE = "http://docs.oasis-open.org/bdxr/ns/XHE/1/ExchangeHeaderEnvelope";
    private static final String XHA_NAMESPACE = "http://docs.oasis-open.org/bdxr/ns/XHE/1/AggregateComponents";
    private static final String XHB_NAMESPACE = "http://docs.oasis-open.org/bdxr/ns/XHE/1/BasicComponents";

    // DBNA XHE Profile constants
    private static final String XHE_VERSION = "1.0";
    private static final String DBNA_CUSTOMIZATION_ID = "http://docs.oasis-open.org/bdxr/ns/XHE/1/ExchangeHeaderEnvelope::XHE##dbnalliance-envelope-1.0";
    private static final String DBNA_PROFILE_ID = "dbnalliance-envelope-1.0";
    private static final String DBNA_CUSTOMIZATION_SCHEME = "bdx-docid-qns";
    private static final String MIME_TYPE = "application/xml";
    private static final String MIME_LIST_ID = "MIME";

    /**
     * Wraps a UBL document in an XHE envelope
     *
     * @param ublDocumentContent The UBL XML document content as string
     * @param senderId Sender party identifier
     * @param receiverId Receiver party identifier
     * @param customizationId Document customization ID (e.g., UBL CustomizationID)
     * @param profileId Document profile ID (e.g., UBL ProfileID)
     * @param messageId Unique message identifier
     * @return XHE envelope XML as string
     * @throws Exception if envelope creation fails
     */
    public String wrapInXHEEnvelope(String ublDocumentContent, String senderId, String receiverId,
                                     String customizationId, String profileId, String messageId) throws Exception {
        logger.info("Creating XHE envelope for document - Sender: {}, Receiver: {}", senderId, receiverId);

        try {
            // Parse UBL document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document ublDocument = builder.parse(new InputSource(new StringReader(ublDocumentContent)));

            // Create XHE root element
            Document xheDocument = builder.newDocument();
            Element xheRoot = xheDocument.createElementNS(XHE_NAMESPACE, "XHE");
            xheRoot.setPrefix("xhe");
            xheDocument.appendChild(xheRoot);

            // Add namespace declarations
            xheRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xhe", XHE_NAMESPACE);
            xheRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xha", XHA_NAMESPACE);
            xheRoot.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xhb", XHB_NAMESPACE);

            // Add XHEVersionID
            addTextElement(xheDocument, xheRoot, XHB_NAMESPACE, "XHEVersionID", XHE_VERSION, "xhb");

            // Add CustomizationID
            Element customizationIdElement = addTextElement(xheDocument, xheRoot, XHB_NAMESPACE, "CustomizationID", DBNA_CUSTOMIZATION_ID, "xhb");
            customizationIdElement.setAttribute("schemeID", DBNA_CUSTOMIZATION_SCHEME);

            // Add ProfileID
            addTextElement(xheDocument, xheRoot, XHB_NAMESPACE, "ProfileID", DBNA_PROFILE_ID, "xhb");

            // Create Header element
            Element headerElement = xheDocument.createElementNS(XHA_NAMESPACE, "Header");
            headerElement.setPrefix("xha");
            xheRoot.appendChild(headerElement);

            // Add Header ID (use message ID)
            addTextElement(xheDocument, headerElement, XHB_NAMESPACE, "ID", messageId, "xhb");

            // Add CreationDateTime (ISO-8601 format)
            String creationDateTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            addTextElement(xheDocument, headerElement, XHB_NAMESPACE, "CreationDateTime", creationDateTime, "xhb");

            // Add FromParty
            Element fromPartyElement = xheDocument.createElementNS(XHA_NAMESPACE, "FromParty");
            fromPartyElement.setPrefix("xha");
            headerElement.appendChild(fromPartyElement);

            Element fromPartyIdentElement = xheDocument.createElementNS(XHA_NAMESPACE, "PartyIdentification");
            fromPartyIdentElement.setPrefix("xha");
            fromPartyElement.appendChild(fromPartyIdentElement);

            addTextElement(xheDocument, fromPartyIdentElement, XHB_NAMESPACE, "ID", senderId, "xhb");

            // Add ToParty
            Element toPartyElement = xheDocument.createElementNS(XHA_NAMESPACE, "ToParty");
            toPartyElement.setPrefix("xha");
            headerElement.appendChild(toPartyElement);

            Element toPartyIdentElement = xheDocument.createElementNS(XHA_NAMESPACE, "PartyIdentification");
            toPartyIdentElement.setPrefix("xha");
            toPartyElement.appendChild(toPartyIdentElement);

            addTextElement(xheDocument, toPartyIdentElement, XHB_NAMESPACE, "ID", receiverId, "xhb");

            // Create Payloads container
            Element payloadsElement = xheDocument.createElementNS(XHA_NAMESPACE, "Payloads");
            payloadsElement.setPrefix("xha");
            xheRoot.appendChild(payloadsElement);

            // Create Payload element
            Element payloadElement = xheDocument.createElementNS(XHA_NAMESPACE, "Payload");
            payloadElement.setPrefix("xha");
            payloadsElement.appendChild(payloadElement);

            // Add ContentTypeCode
            Element contentTypeElement = addTextElement(xheDocument, payloadElement, XHB_NAMESPACE, "ContentTypeCode", MIME_TYPE, "xhb");
            contentTypeElement.setAttribute("listID", MIME_LIST_ID);

            // Add Payload CustomizationID
            addTextElement(xheDocument, payloadElement, XHB_NAMESPACE, "CustomizationID", customizationId != null ? customizationId : "", "xhb");

            // Add Payload ProfileID
            addTextElement(xheDocument, payloadElement, XHB_NAMESPACE, "ProfileID", profileId != null ? profileId : "", "xhb");

            // Add InstanceEncryptionIndicator (false for non-encrypted payloads)
            addTextElement(xheDocument, payloadElement, XHB_NAMESPACE, "InstanceEncryptionIndicator", "false", "xhb");

            // Create PayloadContent and embed UBL document
            Element payloadContentElement = xheDocument.createElementNS(XHA_NAMESPACE, "PayloadContent");
            payloadContentElement.setPrefix("xha");
            payloadElement.appendChild(payloadContentElement);

            // Import UBL root element into XHE document
            Element ublRootClone = (Element) xheDocument.importNode(ublDocument.getDocumentElement(), true);
            payloadContentElement.appendChild(ublRootClone);

            // Convert XHE document to string
            String xheEnvelopeContent = documentToString(xheDocument);

            logger.info("XHE envelope created successfully");
            logger.debug("XHE envelope preview (first 300 chars): {}", xheEnvelopeContent.length() > 300 ?
                xheEnvelopeContent.substring(0, 300) + "..." : xheEnvelopeContent);

            return xheEnvelopeContent;

        } catch (Exception e) {
            logger.error("Failed to create XHE envelope: {}", e.getMessage(), e);
            throw new Exception("Failed to wrap UBL document in XHE envelope: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to add a text element to a parent element
     */
    private Element addTextElement(Document doc, Element parent, String namespace, String localName,
                                   String textContent, String prefix) {
        Element element = doc.createElementNS(namespace, localName);
        element.setPrefix(prefix);
        element.setTextContent(textContent);
        parent.appendChild(element);
        return element;
    }

    /**
     * Convert XML Document to String
     */
    private String documentToString(Document document) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer()
            .transform(new DOMSource(document), new StreamResult(outputStream));
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}








