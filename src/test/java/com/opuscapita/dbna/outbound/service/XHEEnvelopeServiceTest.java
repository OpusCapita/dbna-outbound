package com.opuscapita.dbna.outbound.service;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class XHEEnvelopeServiceTest {

    private static final String XHA = "http://docs.oasis-open.org/bdxr/ns/XHE/1/AggregateComponents";
    private static final String XHB = "http://docs.oasis-open.org/bdxr/ns/XHE/1/BasicComponents";
    private static final String UBL = "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>";
    private static final String CUSTOMIZATION_ID =
        "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##DBNAlliance-1.0-data-Core";

    private final XHEEnvelopeService service = new XHEEnvelopeService();

    private Document wrap(String sender, String receiver, String customizationId, String profileId) throws Exception {
        String xml = service.wrapInXHEEnvelope(UBL, sender, receiver, customizationId, profileId, "msg-1");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Element partyId(Document doc, String party) {
        Element partyElement = (Element) doc.getElementsByTagNameNS(XHA, party).item(0);
        NodeList ids = partyElement.getElementsByTagNameNS(XHB, "ID");
        assertEquals(1, ids.getLength(), party + " must contain exactly one ID");
        return (Element) ids.item(0);
    }

    private static Element payload(Document doc) {
        return (Element) doc.getElementsByTagNameNS(XHA, "Payload").item(0);
    }

    private static Element directChild(Element parent, String ns, String localName) {
        for (var n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    @Test
    void partyIdentifiersAreSplitIntoSchemeIdAndValue() throws Exception {
        Document doc = wrap("FI:OVT::003728468254", "DUNS::116902221", CUSTOMIZATION_ID, "bdx:noprocess");

        Element from = partyId(doc, "FromParty");
        assertEquals("FI:OVT", from.getAttribute("schemeID"));
        assertEquals("003728468254", from.getTextContent());

        Element to = partyId(doc, "ToParty");
        assertEquals("DUNS", to.getAttribute("schemeID"));
        assertEquals("116902221", to.getTextContent());
    }

    @Test
    void payloadHasOrdinalIdAndCustomizationScheme() throws Exception {
        Element payload = payload(wrap("FI:OVT::003728468254", "DUNS::116902221", CUSTOMIZATION_ID, "bdx:noprocess"));

        assertEquals("1", directChild(payload, XHB, "ID").getTextContent());
        Element customization = directChild(payload, XHB, "CustomizationID");
        assertEquals(CUSTOMIZATION_ID, customization.getTextContent());
        assertEquals("bdx-docid-qns", customization.getAttribute("schemeID"));
        Element profile = directChild(payload, XHB, "ProfileID");
        assertEquals("bdx:noprocess", profile.getTextContent());
        assertFalse(profile.hasAttribute("schemeID"), "Process identifiers must not carry schemeID");
    }

    @Test
    void emptyPayloadCustomizationAndProfileAreOmitted() throws Exception {
        Element payload = payload(wrap("FI:OVT::003728468254", "DUNS::116902221", "", ""));

        assertNull(directChild(payload, XHB, "CustomizationID"));
        assertNull(directChild(payload, XHB, "ProfileID"));
    }

    @Test
    void identifierWithoutSchemeIsRejected() {
        Exception e = assertThrows(Exception.class,
            () -> wrap("003728468254", "DUNS::116902221", CUSTOMIZATION_ID, "bdx:noprocess"));
        assertTrue(e.getMessage().contains("{scheme}::{identifier}"));
    }
}

