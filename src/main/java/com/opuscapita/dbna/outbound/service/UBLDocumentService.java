package com.opuscapita.dbna.outbound.service;

import com.helger.commons.io.stream.StringInputStream;
import com.helger.ubl23.UBL23Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service for handling UBL 2.3 documents
 */
@Service
public class UBLDocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(UBLDocumentService.class);
    
    /**
     * Validate UBL 2.3 document
     */
    public boolean validateUBLDocument(String ublXml) {
        try {
            // Try to parse as Invoice (most common UBL document)
            Object ublDoc = UBL23Marshaller.invoice().read(
                new StringInputStream(ublXml, StandardCharsets.UTF_8)
            );
            
            if (ublDoc != null) {
                logger.info("Valid UBL 2.3 Invoice document");
                return true;
            }
            
            // Try other UBL document types if needed
            ublDoc = UBL23Marshaller.order().read(
                new StringInputStream(ublXml, StandardCharsets.UTF_8)
            );
            
            if (ublDoc != null) {
                logger.info("Valid UBL 2.3 Order document");
                return true;
            }
            
            logger.warn("Document is not a recognized UBL 2.3 type");
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to validate UBL 2.3 document", e);
            return false;
        }
    }
    
    /**
     * Extract document type from UBL XML
     */
    public String extractDocumentType(String ublXml) {
        if (ublXml.contains("<Invoice")) {
            return "Invoice";
        } else if (ublXml.contains("<Order")) {
            return "Order";
        } else if (ublXml.contains("<Despatch")) {
            return "DespatchAdvice";
        } else if (ublXml.contains("<Receipt")) {
            return "ReceiptAdvice";
        } else if (ublXml.contains("<CreditNote")) {
            return "CreditNote";
        } else if (ublXml.contains("<DebitNote")) {
            return "DebitNote";
        }
        return "Unknown";
    }
}



