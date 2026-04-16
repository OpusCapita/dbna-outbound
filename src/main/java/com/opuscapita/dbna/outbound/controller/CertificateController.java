package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.config.AS4Configuration;
import com.opuscapita.dbna.outbound.util.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for certificate management and information
 */
@RestController
@RequestMapping("/testapi/certificates")
public class CertificateController {
    
    private static final Logger logger = LoggerFactory.getLogger(CertificateController.class);
    
    @Autowired
    private AS4Configuration as4Configuration;
    
    /**
     * Get certificate status and information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCertificateStatus() {
        logger.info("Retrieving certificate status");
        
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Check keystore
            boolean keystoreConfigured = as4Configuration.isKeystoreConfigured();
            status.put("keystoreConfigured", keystoreConfigured);
            status.put("keystorePath", as4Configuration.getKeystorePath());
            
            if (keystoreConfigured) {
                KeyStore keyStore = CertificateUtil.loadKeyStore(
                    as4Configuration.getKeystorePath(),
                    System.getenv("KEYSTORE_PASSWORD") != null ? 
                        System.getenv("KEYSTORE_PASSWORD") : "changeit",
                    System.getenv("KEYSTORE_TYPE") != null ? 
                        System.getenv("KEYSTORE_TYPE") : "JKS"
                );
                
                if (keyStore != null) {
                    String alias = as4Configuration.getKeyAlias();
                    boolean valid = CertificateUtil.validateCertificate(keyStore, alias);
                    boolean expiringSoon = CertificateUtil.isCertificateExpiringSoon(keyStore, alias, 30);
                    
                    status.put("certificateValid", valid);
                    status.put("certificateExpiringSoon", expiringSoon);
                    status.put("certificateAlias", alias);
                    
                    // Get certificate info (excluding sensitive data)
                    String certInfo = CertificateUtil.getCertificateInfo(keyStore, alias);
                    status.put("certificateInfo", certInfo);
                } else {
                    status.put("error", "Failed to load keystore");
                }
            }
            
            // Check truststore
            status.put("truststoreConfigured", as4Configuration.isTruststoreConfigured());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error retrieving certificate status", e);
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }
    
    /**
     * List all available certificate aliases in the keystore
     */
    @GetMapping("/aliases")
    public ResponseEntity<Map<String, Object>> listAliases() {
        logger.info("Listing certificate aliases");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!as4Configuration.isKeystoreConfigured()) {
                response.put("error", "Keystore not configured");
                return ResponseEntity.badRequest().body(response);
            }
            
            KeyStore keyStore = CertificateUtil.loadKeyStore(
                as4Configuration.getKeystorePath(),
                System.getenv("KEYSTORE_PASSWORD") != null ? 
                    System.getenv("KEYSTORE_PASSWORD") : "changeit",
                System.getenv("KEYSTORE_TYPE") != null ? 
                    System.getenv("KEYSTORE_TYPE") : "JKS"
            );
            
            if (keyStore != null) {
                String aliases = CertificateUtil.listAliases(keyStore);
                response.put("aliases", aliases);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to load keystore");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error listing certificate aliases", e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Validate a specific certificate by alias
     */
    @GetMapping("/validate/{alias}")
    public ResponseEntity<Map<String, Object>> validateCertificate(@PathVariable String alias) {
        logger.info("Validating certificate with alias: {}", alias);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!as4Configuration.isKeystoreConfigured()) {
                response.put("error", "Keystore not configured");
                return ResponseEntity.badRequest().body(response);
            }
            
            KeyStore keyStore = CertificateUtil.loadKeyStore(
                as4Configuration.getKeystorePath(),
                System.getenv("KEYSTORE_PASSWORD") != null ? 
                    System.getenv("KEYSTORE_PASSWORD") : "changeit",
                System.getenv("KEYSTORE_TYPE") != null ? 
                    System.getenv("KEYSTORE_TYPE") : "JKS"
            );
            
            if (keyStore != null) {
                boolean valid = CertificateUtil.validateCertificate(keyStore, alias);
                boolean expiringSoon = CertificateUtil.isCertificateExpiringSoon(keyStore, alias, 30);
                String certInfo = CertificateUtil.getCertificateInfo(keyStore, alias);
                
                response.put("alias", alias);
                response.put("valid", valid);
                response.put("expiringSoon", expiringSoon);
                response.put("certificateInfo", certInfo);
                
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to load keystore");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error validating certificate", e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}

