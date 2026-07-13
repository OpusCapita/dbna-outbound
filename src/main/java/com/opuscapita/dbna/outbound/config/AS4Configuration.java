package com.opuscapita.dbna.outbound.config;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.AS4CryptoProperties;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.scope.mgr.ScopeManager;
import com.opuscapita.dbna.outbound.service.TruststoreManager;
import lombok.Getter;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Arrays;
/**
 * Configuration for AS4 protocol with X.509 certificate support
 * Supports both keystore (client authentication) and truststore (server certificate validation)
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
    @Value("${as4.truststore.password:changeit}")
    private String truststorePassword;
    @Value("${as4.truststore.type:JKS}")
    private String truststoreType;
    @Value("${as4.ssl.enabled:true}")
    private boolean sslEnabled;
    @Value("${as4.ssl.verify-hostname:true}")
    private boolean verifyHostname;
    @Value("${as4.ssl.protocol:TLS}")
    private String sslProtocol;

    private final TruststoreManager truststoreManager;
    private final Environment environment;

    @Autowired
    public AS4Configuration(TruststoreManager truststoreManager, Environment environment) {
        this.truststoreManager = truststoreManager;
        this.environment = environment;
    }
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
            
            // Load truststore from TruststoreManager (which handles dynamic cert injection)
            logger.debug("Retrieving truststore from TruststoreManager...");
            KeyStore trustStore = null;
            try {
                trustStore = truststoreManager.getTruststore();
                if (trustStore != null) {
                    logger.info("✓ Truststore loaded from TruststoreManager for SSL server certificate validation");
                } else {
                    logger.warn("✗ Truststore is null - this is unexpected");
                }
            } catch (Exception e) {
                logger.error("✗ Failed to get truststore from TruststoreManager: {}", e.getMessage(), e);
            }
            
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.setProtocol(sslProtocol);
            
            if (keyStore != null) {
                // Try to load key material with the configured key password
                if (!loadKeyMaterialWithPasswordFallback(sslContextBuilder, keyStore)) {
                    logger.warn("Could not load key material with any available password. Proceeding without client certificate.");
                }
            }
            
            // Load trust material from truststore or use system default
            if (trustStore != null) {
                sslContextBuilder.loadTrustMaterial(trustStore, null);
                logger.info("✓ SSL configured with truststore for certificate validation");
            } else {
                // No truststore available - use permissive trust for development
                logger.warn("⚠ Truststore is null/unavailable - configuring permissive SSL as fallback");
                sslContextBuilder.loadTrustMaterial(null, (chain, authType) -> {
                    logger.debug("Accepting certificate in fallback mode: {}",
                        chain != null && chain.length > 0 ? chain[0].getSubjectX500Principal() : "unknown");
                    return true;
                });
                logger.warn("SSL configured without truststore validation - accepting all certificates (FALLBACK MODE)");
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
            logger.error("✗ Failed to configure secure HTTP client. Falling back to default client.", e);
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
     * Tries filesystem first, then classpath resources (with fallback to temp file extraction for JAR resources)
     */
    private String resolveKeystorePath(String path) {
         try {
             // Strip 'classpath:' prefix if present
             String normalizedPath = path;
             if (normalizedPath.startsWith("classpath:")) {
                 normalizedPath = normalizedPath.substring("classpath:".length());
                 logger.debug("Stripped 'classpath:' prefix from path: {} -> {}", path, normalizedPath);
             }

             // Try direct file system path
             File file = new File(normalizedPath);
             if (file.exists() && file.isFile()) {
                 logger.debug("Found keystore at filesystem path: {}", file.getAbsolutePath());
                 return file.getAbsolutePath();
             }

             // Try classpath root
             Resource resource = new ClassPathResource(normalizedPath);
             if (resource.exists()) {
                 try {
                     String absolutePath = resource.getFile().getAbsolutePath();
                     logger.debug("Found keystore at classpath path: {}", absolutePath);
                     return absolutePath;
                 } catch (Exception e) {
                     // Classpath resource is inside a JAR - extract to temp location
                     logger.debug("Classpath resource is inside JAR, extracting to temp location: {}", e.getMessage());
                     return extractKeystoreFromClasspath(resource, normalizedPath);
                 }
             }

             // Try keystores/ classpath subdirectory
             if (!normalizedPath.startsWith("keystores/")) {
                 resource = new ClassPathResource("keystores/" + normalizedPath);
                 if (resource.exists()) {
                     try {
                         String absolutePath = resource.getFile().getAbsolutePath();
                         logger.debug("Found keystore at classpath/keystores path: {}", absolutePath);
                         return absolutePath;
                     } catch (Exception e) {
                         // Classpath resource is inside a JAR - extract to temp location
                         logger.debug("Classpath resource is inside JAR, extracting to temp location: {}", e.getMessage());
                         return extractKeystoreFromClasspath(resource, "keystores/" + normalizedPath);
                     }
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
     * Extract a classpath resource (keystore file) to a temporary location
     * This is necessary when the keystore is packaged inside a JAR and Phase4/WSS4J needs filesystem access
     */
    private String extractKeystoreFromClasspath(Resource resource, String resourcePath) {
        try {
            // Create a temp file with a meaningful name
            String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "dbna-as4-keystores");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
                logger.debug("Created temporary keystore directory: {}", tempDir.getAbsolutePath());
            }

            File tempFile = new File(tempDir, filename);

            // Extract the resource to the temp file
            try (var inputStream = resource.getInputStream();
                 var outputStream = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            logger.info("Extracted keystore from classpath to temporary location: {}", tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            logger.error("Error extracting keystore from classpath: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Load a keystore from file system or classpath
     */
    private KeyStore loadKeyStoreFromResource(String path, String password, String type) {
         try {
             // Strip 'classpath:' prefix if present
             String normalizedPath = path;
             if (normalizedPath.startsWith("classpath:")) {
                 normalizedPath = normalizedPath.substring("classpath:".length());
             }

             // Normalize keystore type based on file extension
             String normalizedType = type;
             if (normalizedPath.toLowerCase().endsWith(".p12") || normalizedPath.toLowerCase().endsWith(".pfx")) {
                 normalizedType = "PKCS12";
             } else if ("P12".equalsIgnoreCase(type)) {
                 // Also handle explicit P12 type configuration
                 normalizedType = "PKCS12";
             }
             KeyStore keyStore = KeyStore.getInstance(normalizedType);

             // Try file system first
             File file = new File(normalizedPath);
             if (file.exists()) {
                 try (FileInputStream fis = new FileInputStream(file)) {
                     keyStore.load(fis, password.toCharArray());
                     return keyStore;
                 }
             }

             // Try classpath
             Resource resource = new ClassPathResource(normalizedPath);
             if (resource.exists()) {
                 keyStore.load(resource.getInputStream(), password.toCharArray());
                 return keyStore;
             }

             // Try classpath with keystores/ prefix
             if (!normalizedPath.startsWith("keystores/")) {
                 resource = new ClassPathResource("keystores/" + normalizedPath);
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

         // Strip 'classpath:' prefix if present
         String normalizedPath = path;
         if (normalizedPath.startsWith("classpath:")) {
             normalizedPath = normalizedPath.substring("classpath:".length());
         }

         // Check file system first
         File file = new File(normalizedPath);
         if (file.exists()) {
             return true;
         }

         // Check classpath
         try {
             Resource resource = new ClassPathResource(normalizedPath);
             if (resource.exists()) {
                 return true;
             }

             // Try with keystores/ prefix
             if (!normalizedPath.startsWith("keystores/")) {
                 resource = new ClassPathResource("keystores/" + normalizedPath);
                 return resource.exists();
             }
         } catch (Exception e) {
             logger.debug("Error checking classpath resource: {}", normalizedPath, e);
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
     *
     * Also configures SSL context for development environments with self-signed certificates.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePhase4GlobalScope() {
        try {
            // Always initialize global scope for Phase4
            // If it's already initialized, this will be a no-op
            logger.info("Initializing Phase4 global scope for MetaAS4Manager");
            ScopeManager.onGlobalBegin("Phase4-Global");
            logger.info("Phase4 global scope initialized successfully");

            // Configure SSL context for Phase4 in development
            // Phase4 uses its own HTTP client and doesn't respect our secureHttpClient bean
            configurePhase4SSLContextForDevelopment();
        } catch (IllegalStateException e) {
            // Global scope might already be active - this is expected on subsequent calls
            if (e.getMessage() != null && e.getMessage().contains("already been begin")) {
                logger.debug("Phase4 global scope is already active");
                configurePhase4SSLContextForDevelopment();
            } else {
                logger.error("Failed to initialize Phase4 global scope. AS4 messaging may fail.", e);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Phase4 global scope. AS4 messaging may fail.", e);
            // Don't throw exception as it might cause application startup failure
            // The error will be visible in logs and caught when AS4 operations are attempted
        }
    }

    /**
     * Configure Phase4's SSL context for both development and production.
     *
     * In DEVELOPMENT: Uses permissive TrustManager to accept self-signed certificates at localhost.
     * In PRODUCTION: Configures SSL context with the managed truststore that receives injected
     *                receiver certificates from SMP queries, enabling proper PKIX validation.
     *
     * Phase4 uses its own HTTP client and doesn't respect our secureHttpClient bean,
     * so we must configure the global SSL context that Phase4 will use.
     */
    private void configurePhase4SSLContextForDevelopment() {
        try {
            // Check Spring's active profiles to determine environment
            String[] activeProfiles = environment.getActiveProfiles();
            boolean isDevelopment = activeProfiles.length == 0 || !Arrays.asList(activeProfiles).contains("prod");

            logger.debug("Active Spring profiles: {}, Development mode: {}",
                Arrays.toString(activeProfiles), isDevelopment);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            
            if (isDevelopment) {
                logger.info("⚙️ Development mode: Configuring Phase4 SSL context with permissive TrustManager for self-signed certificates");
                
                // Create a permissive TrustManager for development
                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                            // Accept all client certificates
                        }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                            logger.debug("Accepting server certificate in development mode: {}",
                                certs != null && certs.length > 0 ? certs[0].getSubjectX500Principal() : "unknown");
                        }
                    }
                };
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                logger.warn("⚠️ Phase4 SSL context configured with permissive TrustManager (DEVELOPMENT MODE)");
            } else {
                logger.info("⚙️ Production mode: Configuring Phase4 SSL context with managed truststore for dynamic certificate injection");
                
                // Get the managed truststore that receives injected receiver certificates
                KeyStore trustStore = truststoreManager.getTruststore();
                if (trustStore == null) {
                    logger.error("✗ Truststore is null in production mode - this is critical");
                    throw new RuntimeException("Truststore unavailable in production mode");
                }
                
                // Create SSL context with the managed truststore
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                
                sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
                logger.info("✓ Phase4 SSL context configured with managed truststore (receiver certificates will be injected from SMP)");
            }
            
            // Set as the global default SSL context for ALL TLS operations in this JVM
            // This ensures Phase4's HTTP client will use this SSL context
            SSLContext.setDefault(sslContext);
            logger.info("✓ Global SSL context set as default for JVM (Phase4 will use this)");
            
        } catch (Exception e) {
            logger.error("✗ Failed to configure Phase4 SSL context: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure SSL context for Phase4: " + e.getMessage(), e);
        }
    }
}
