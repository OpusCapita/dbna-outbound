package com.opuscapita.dbna.outbound.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading test resources
 */
public class TestResourceLoader {

    private static final String UBL_RESOURCE_PATH = "/ubl/";

    /**
     * Load UBL file content from test resources
     * 
     * @param filename the filename in the ubl directory
     * @return the file content as string
     * @throws IOException if file cannot be read
     */
    public static String loadUblFile(String filename) throws IOException {
        String resourcePath = UBL_RESOURCE_PATH + filename;
        try (InputStream is = TestResourceLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Load test invoice UBL
     */
    public static String loadTestInvoice() throws IOException {
        return loadUblFile("test-invoice.xml");
    }

    /**
     * Load DBNA example invoice
     */
    public static String loadDbnaInvoice() throws IOException {
        return loadUblFile("DBNA000005UBLInvoice.xml");
    }

    /**
     * Load test order UBL
     */
    public static String loadTestOrder() throws IOException {
        return loadUblFile("test-order.xml");
    }

    /**
     * Load test despatch advice UBL
     */
    public static String loadTestDespatchAdvice() throws IOException {
        return loadUblFile("test-despatch-advice.xml");
    }

    /**
     * Load invalid document for negative testing
     */
    public static String loadInvalidDocument() throws IOException {
        return loadUblFile("invalid-document.xml");
    }

    /**
     * Load UBL file as InputStream
     */
    public static InputStream loadUblFileAsStream(String filename) {
        String resourcePath = UBL_RESOURCE_PATH + filename;
        return TestResourceLoader.class.getResourceAsStream(resourcePath);
    }
}

