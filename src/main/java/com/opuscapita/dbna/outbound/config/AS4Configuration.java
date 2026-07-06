package com.opuscapita.dbna.outbound.config;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.AS4CryptoProperties;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.scope.mgr.ScopeManager;
import lombok.Getter;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
/**
 * Configuration for AS4 protocol with X.509 certificate support
 * Note: Truststore support has been removed. Certificate verification is handled at the protocol level.
 */
@Configuration
@Getter
public class AS4Configuration {
    private static final Logger logger = LoggerFactory.getLogger(AS4Configuration.class);
    @Value("${as4.keystore.path:keystore.jks}")
    private String keystorePath;
    @Value("${as4.keystore.password:changeit}")
    private String keystorePassword;
    @Value("${as4.keystore.type:JKS}")
    private String keystoreType;
    @Value("${as4.key.alias:dbna}")
    private String keyAlias;
    @Value("${as4.key.password:changeit}")
    private String keyPassword;
    @Value("${as4.ssl.enabled:true}")
    private boolean sslEnabled;
    @Value("${as4.ssl.verify-hostname:true}")
    private boolean verifyHostname;
    @Value("${as4.ssl.protocol:TLS}")
    private String sslProtocol;
    @Bean
    public IAS4CryptoFactory as4CryptoFactory() {
        logger.info("Initializing AS4 crypto factory with X.509 certificate support");
        AS4CryptoProperties cryptoProps = new AS4CryptoProperties();
        
        // Resolve keystore path to absolute path so Phase4's WSS4J can find it
        String resolvedKeystorePath = resolveKeystorePath(keystorePath);

        if (resolvedKeystorePath != null && resourceExists(keystorePath)) {
            logger.info("Loading keystore from: {}", keystorePath);
            logger.debug("Resolved keystore path: {}", resolvedKeystorePath);
            // Pass the absolute path so WSS4J can load it
            cryptoProps.setKeyStorePath(resolvedKeystorePath);
            cryptoProps.setKeyStorePassword(keystorePassword);

            // Auto-detect keystore type based on actual file extension (resolved path)
            // This handles cases where default config says .jks but actual file is .p12
            String detectedType = keystoreType;
            if (resolvedKeystorePath.toLowerCase().endsWith(".p12") || resolvedKeystorePath.toLowerCase().endsWith(".pfx")) {
                logger.debug("Detected PKCS12 keystore file format from resolved path, overriding configured type '{}' with PKCS12", keystoreType);
                detectedType = "PKCS12";
            }

            cryptoProps.setKeyStoreType(EKeyStoreType.getFromIDCaseInsensitiveOrDefault(detectedType, EKeyStoreType.JKS));
            cryptoProps.setKeyAlias(keyAlias);
            cryptoProps.setKeyPassword(keyPassword);
            logger.info("Keystore loaded successfully with alias: {} using type: {}", keyAlias, detectedType);

            // Validate keystore contents for troubleshooting
            validateKeystoreContents(resolvedKeystorePath, detectedType);
        } else {
            logger.warn("Keystore file not found at: {}. AS4 signing will not be available.", keystorePath);
        }
        return new AS4CryptoFactoryProperties(cryptoProps);
    }
    @Bean
    public CloseableHttpClient secureHttpClient() {
        try {
            if (!sslEnabled) {
                logger.info("SSL is disabled, using default HTTP client");
                return HttpClients.createDefault();
            }
            logger.info("Configuring secure HTTP client with SSL/TLS support");
            
            KeyStore keyStore = loadKeyStoreFromResource(keystorePath, keystorePassword, keystoreType);
            if (keyStore != null) {
                logger.info("Keystore loaded for SSL client authentication");
            }
            
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.setProtocol(sslProtocol);
            if (keyStore != null) {
                // Try to load key material with the configured key password
                if (!loadKeyMaterialWithPasswordFallback(sslContextBuilder, keyStore)) {
                    logger.warn("Could not load key material with any available password. Proceeding without client certificate.");
                }
            }
            SSLContext sslContext = sslContextBuilder.build();
            SSLConnectionSocketFactory sslSocketFactory;
            if (verifyHostname) {
                sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
                logger.info("SSL configured with hostname verification enabled");
            } else {
                sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
                logger.warn("SSL configured with hostname verification DISABLED - not recommended for production!");
            }
            return HttpClients.custom()
                .setConnectionManager(
                    PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build()
                )
                .build();
        } catch (Exception e) {
            logger.error("Failed to configure secure HTTP client. Falling back to default client.", e);
            return HttpClients.createDefault();
        }
    }
    
     /**
      * Try to load key material with multiple password attempts.
      * For PKCS12 keystores, the keystore password and key password are often the same.
      */
     private boolean loadKeyMaterialWithPasswordFallback(SSLContextBuilder sslContextBuilder, KeyStore keyStore) {
         // Try with configured key password first
         try {
             sslContextBuilder.loadKeyMaterial(keyStore, keyPassword.toCharArray());
             logger.debug("Successfully loaded key material with configured key password");
             return true;
         } catch (java.security.UnrecoverableKeyException e) {
             logger.debug("Failed to load key material with configured key password, trying keystore password");

             // For PKCS12, try with keystore password (they are often the same)
             try {
                 sslContextBuilder.loadKeyMaterial(keyStore, keystorePassword.toCharArray());
                 logger.info("Successfully loaded key material using keystore password as key password");
                 return true;
             } catch (Exception ex) {
                 logger.warn("Failed to load key material with both key password and keystore password", ex);
                 return false;
             }
         } catch (Exception e) {
             logger.warn("Error loading key material: {}", e.getMessage(), e);
             return false;
         }
     }


    /**
     * Validate keystore contents and log diagnostic information
     * This helps troubleshoot "No certificates for user" errors from WSS4J
     */
    private void validateKeystoreContents(String keystorePath, String keystoreType) {
        try {
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }

            java.util.Enumeration<String> aliases = keyStore.aliases();
            boolean foundKeyAlias = false;
            StringBuilder aliasInfo = new StringBuilder("Keystore contents:\n");

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                boolean isKeyEntry = keyStore.isKeyEntry(alias);
                boolean isCertEntry = keyStore.isCertificateEntry(alias);
                aliasInfo.append("  - ").append(alias);

                if (isKeyEntry) {
                    aliasInfo.append(" (PRIVATE KEY ENTRY)");
                    if (alias.equals(keyAlias)) {
                        foundKeyAlias = true;
                    }
                } else if (isCertEntry) {
                    aliasInfo.append(" (CERTIFICATE ENTRY)");
                } else {
                    aliasInfo.append(" (OTHER)");
                }
                aliasInfo.append("\n");
            }

            logger.debug(aliasInfo.toString());

            if (!foundKeyAlias) {
                logger.warn("CRITICAL: Keystore does not contain a private key entry with alias '{}'. " +
                    "This will cause 'No certificates for user' error during signing.", keyAlias);
            } else {
                logger.debug("Successfully verified that keystore contains private key entry for alias '{}'", keyAlias);
            }
        } catch (Exception e) {
            logger.warn("Could not validate keystore contents (this is not critical): {}", e.getMessage());
        }
    }

    /**
     * Resolve keystore path to absolute file path for Phase4's WSS4J
     * Tries filesystem first, then classpath resources
     */
    private String resolveKeystorePath(String path) {
        try {
            // Try direct file system path
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                logger.debug("Found keystore at filesystem path: {}", file.getAbsolutePath());
                return file.getAbsolutePath();
            }

            // Try classpath root
            Resource resource = new ClassPathResource(path);
            if (resource.exists()) {
                String absolutePath = resource.getFile().getAbsolutePath();
                logger.debug("Found keystore at classpath path: {}", absolutePath);
                return absolutePath;
            }

            // Try keystores/ classpath subdirectory
            if (!path.startsWith("keystores/")) {
                resource = new ClassPathResource("keystores/" + path);
                if (resource.exists()) {
                    String absolutePath = resource.getFile().getAbsolutePath();
                    logger.debug("Found keystore at classpath/keystores path: {}", absolutePath);
                    return absolutePath;
                }
            }

            logger.warn("Could not resolve keystore path: {}", path);
            return null;
        } catch (Exception e) {
            logger.error("Error resolving keystore path: {}", path, e);
            return null;
        }
    }

    /**
     * Load a keystore from file system or classpath
     */
    private KeyStore loadKeyStoreFromResource(String path, String password, String type) {
        try {
            // Normalize keystore type based on file extension
            String normalizedType = type;
            if (path.toLowerCase().endsWith(".p12") || path.toLowerCase().endsWith(".pfx")) {
                normalizedType = "PKCS12";
            } else if ("P12".equalsIgnoreCase(type)) {
                // Also handle explicit P12 type configuration
                normalizedType = "PKCS12";
            }
            KeyStore keyStore = KeyStore.getInstance(normalizedType);

            // Try file system first
            File file = new File(path);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    keyStore.load(fis, password.toCharArray());
                    return keyStore;
                }
            }
            
            // Try classpath
            Resource resource = new ClassPathResource(path);
            if (resource.exists()) {
                keyStore.load(resource.getInputStream(), password.toCharArray());
                return keyStore;
            }
            
            // Try classpath with keystores/ prefix
            if (!path.startsWith("keystores/")) {
                resource = new ClassPathResource("keystores/" + path);
                if (resource.exists()) {
                    keyStore.load(resource.getInputStream(), password.toCharArray());
                    return keyStore;
                }
            }
            
            logger.warn("Keystore not found at: {}", path);
            return null;
        } catch (Exception e) {
            logger.error("Error loading keystore from: {}", path, e);
            return null;
        }
    }
    
    /**
     * Check if a resource exists in file system or classpath
     */
    private boolean resourceExists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // Check file system first
        File file = new File(path);
        if (file.exists()) {
            return true;
        }
        
        // Check classpath
        try {
            Resource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return true;
            }
            
            // Try with keystores/ prefix
            if (!path.startsWith("keystores/")) {
                resource = new ClassPathResource("keystores/" + path);
                return resource.exists();
            }
        } catch (Exception e) {
            logger.debug("Error checking classpath resource: {}", path, e);
        }
        
        return false;
    }
    
    public boolean isKeystoreConfigured() {
        return resourceExists(keystorePath);
    }

    /**
     * Initialize Phase4 global scope on application startup.
     * This is required by Phase4's MetaAS4Manager which expects a global scope to be available.
     * The global scope is a thread-local scope that Phase4 uses for accessing configuration and state.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePhase4GlobalScope() {
        try {
            // Always initialize global scope for Phase4
            // If it's already initialized, this will be a no-op
            logger.info("Initializing Phase4 global scope for MetaAS4Manager");
            ScopeManager.onGlobalBegin("Phase4-Global");
            logger.info("Phase4 global scope initialized successfully");
        } catch (IllegalStateException e) {
            // Global scope might already be active - this is expected on subsequent calls
            if (e.getMessage() != null && e.getMessage().contains("already been begin")) {
                logger.debug("Phase4 global scope is already active");
            } else {
                logger.error("Failed to initialize Phase4 global scope. AS4 messaging may fail.", e);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Phase4 global scope. AS4 messaging may fail.", e);
            // Don't throw exception as it might cause application startup failure
            // The error will be visible in logs and caught when AS4 operations are attempted
        }
    }
}
