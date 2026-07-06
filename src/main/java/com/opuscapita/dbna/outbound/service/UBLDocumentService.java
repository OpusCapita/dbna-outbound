package com.opuscapita.dbna.outbound.service;

import com.helger.commons.io.stream.StringInputStream;
import com.helger.ubl23.UBL23Marshaller;
import com.opuscapita.dbna.outbound.exception.DocumentValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * Service for handling UBL 2.3 documents
 */
@Service
public class UBLDocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(UBLDocumentService.class);

    /**
     * Validate that the document is a valid UBL 2.3 XML document.
     * Throws DocumentValidationException if validation fails.
     *
     * Supports DBNA-relevant UBL 2.3 document types:
     * - Invoice (Core and Extended)
     * - CreditNote
     * - DebitNote
     * - Order
     * - OrderResponse
     * - DespatchAdvice
     * - ReceiptAdvice
     * - RemittanceAdvice
     * - ApplicationResponse
     *
     * @param ublXml UBL XML document content as string
     * @throws DocumentValidationException if document is not valid UBL 2.3 XML
     */
    public void validateUBLDocument(String ublXml) {
        // First validate it's well-formed XML
        validateXMLStructure(ublXml);

        // Then validate it's a recognized UBL 2.3 document type
        validateUBLDocumentType(ublXml);
    }

    /**
     * Validate that the document content is well-formed XML
     */
    private void validateXMLStructure(String ublXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(ublXml)));

            logger.debug("XML structure validation passed");
        } catch (Exception e) {
            logger.error("XML structure validation failed: {}", e.getMessage());
            throw new DocumentValidationException(
                "Invalid XML structure: " + e.getMessage(), e);
        }
    }

    /**
     * Validate that the document is a recognized UBL 2.3 document type
     */
    private void validateUBLDocumentType(String ublXml) {
        String detectedType = null;

        // Try parsing as each supported UBL document type
        if (tryParseAs("Invoice", ublXml)) {
            detectedType = "Invoice";
        } else if (tryParseAs("CreditNote", ublXml)) {
            detectedType = "CreditNote";
        } else if (tryParseAs("DebitNote", ublXml)) {
            detectedType = "DebitNote";
        } else if (tryParseAs("Order", ublXml)) {
            detectedType = "Order";
        } else if (tryParseAs("OrderResponse", ublXml)) {
            detectedType = "OrderResponse";
        } else if (tryParseAs("DespatchAdvice", ublXml)) {
            detectedType = "DespatchAdvice";
        } else if (tryParseAs("ReceiptAdvice", ublXml)) {
            detectedType = "ReceiptAdvice";
        } else if (tryParseAs("ApplicationResponse", ublXml)) {
            detectedType = "ApplicationResponse";
        } else if (tryParseAs("RemittanceAdvice", ublXml)) {
            detectedType = "RemittanceAdvice";
        }

        if (detectedType == null) {
            throw new DocumentValidationException(
                "Document is not a valid UBL 2.3 document type. " +
                "Supported types: Invoice, CreditNote, DebitNote, Order, OrderResponse, " +
                "DespatchAdvice, ReceiptAdvice, ApplicationResponse, RemittanceAdvice");
        }

        logger.info("Valid UBL 2.3 {} document", detectedType);
    }

    /**
     * Try to parse XML as a specific UBL document type
     */
    private boolean tryParseAs(String docType, String ublXml) {
        try {
            StringInputStream input = new StringInputStream(ublXml, StandardCharsets.UTF_8);

            Object ublDoc = switch (docType) {
                case "Invoice" -> UBL23Marshaller.invoice().read(input);
                case "CreditNote" -> UBL23Marshaller.creditNote().read(input);
                case "DebitNote" -> UBL23Marshaller.debitNote().read(input);
                case "Order" -> UBL23Marshaller.order().read(input);
                case "OrderResponse" -> UBL23Marshaller.orderResponse().read(input);
                case "DespatchAdvice" -> UBL23Marshaller.despatchAdvice().read(input);
                case "ReceiptAdvice" -> UBL23Marshaller.receiptAdvice().read(input);
                case "ApplicationResponse" -> UBL23Marshaller.applicationResponse().read(input);
                case "RemittanceAdvice" -> tryParseRemittanceAdvice(ublXml);
                default -> null;
            };

            return ublDoc != null;
        } catch (Exception e) {
            logger.debug("Failed to parse as {}: {}", docType, e.getMessage());
            return false;
        }
    }

    /**
     * Try to parse RemittanceAdvice - not all versions have a direct marshaller method
     */
    private Object tryParseRemittanceAdvice(String ublXml) {
        try {
            StringInputStream input = new StringInputStream(ublXml, StandardCharsets.UTF_8);
            return UBL23Marshaller.remittanceAdvice().read(input);
        } catch (NoSuchMethodError e) {
            // RemittanceAdvice might not be directly supported, check by XML structure instead
            if (ublXml.contains("<RemittanceAdvice")) {
                return new Object(); // Placeholder indicating valid structure
            }
            throw e;
        }
    }


    /**
     * Extract document type from UBL XML content
     */
    public String extractDocumentType(String ublXml) {
        if (ublXml.contains("<Invoice")) {
            return "Invoice";
        } else if (ublXml.contains("<Order")) {
            return "Order";
        } else if (ublXml.contains("<DespatchAdvice")) {
            return "DespatchAdvice";
        } else if (ublXml.contains("<ReceiptAdvice")) {
            return "ReceiptAdvice";
        } else if (ublXml.contains("<CreditNote")) {
            return "CreditNote";
        } else if (ublXml.contains("<DebitNote")) {
            return "DebitNote";
        } else if (ublXml.contains("<RemittanceAdvice")) {
            return "RemittanceAdvice";
        } else if (ublXml.contains("<StatementOfAccount")) {
            return "StatementOfAccount";
        } else if (ublXml.contains("<ApplicationResponse")) {
            return "ApplicationResponse";
        } else if (ublXml.contains("<OrderResponse")) {
            return "OrderResponse";
        }
        return "Unknown";
    }
}



