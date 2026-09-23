package com.opuscapita.dbna.outbound.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for XML operations
 */
@Slf4j
public class XmlUtil {

    /**
     * Pretty-prints XML content with proper indentation.
     * Removes empty lines from the output for cleaner log display.
     *
     * @param xmlBytes the XML bytes to format
     * @return formatted XML string with indentation and no empty lines, or raw XML if formatting fails
     */
    public static String prettyPrintXml(byte[] xmlBytes) {
        try {
            String rawXml = new String(xmlBytes, StandardCharsets.UTF_8);
            DocumentBuilderFactory dbfPretty = DocumentBuilderFactory.newInstance();
            dbfPretty.setNamespaceAware(true);
            DocumentBuilder db = dbfPretty.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(rawXml)));

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(sw));

            // Remove empty lines from the formatted output (including newline characters)
            String formattedXml = sw.toString();
            return formattedXml.replaceAll("(?m)^[ \\t]*\\r?\\n", "");
        } catch (Exception e) {
            // If formatting fails, return raw XML
            log.debug("Failed to pretty-print XML: {}", e.getMessage());
            return new String(xmlBytes, StandardCharsets.UTF_8);
        }
    }
}
