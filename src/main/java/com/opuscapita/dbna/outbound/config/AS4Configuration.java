package com.opuscapita.dbna.outbound.config;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.AS4CryptoProperties;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.security.keystore.EKeyStoreType;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        
        if (resourceExists(keystorePath)) {
            logger.info("Loading keystore from: {}", keystorePath);
            cryptoProps.setKeyStorePath(keystorePath);
            cryptoProps.setKeyStorePassword(keystorePassword);
            cryptoProps.setKeyStoreType(EKeyStoreType.getFromIDCaseInsensitiveOrDefault(keystoreType, EKeyStoreType.JKS));
            cryptoProps.setKeyAlias(keyAlias);
            cryptoProps.setKeyPassword(keyPassword);
            logger.info("Keystore loaded successfully with alias: {}", keyAlias);
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
                sslContextBuilder.loadKeyMaterial(keyStore, keyPassword.toCharArray());
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
     * Load a keystore from file system or classpath
     */
    private KeyStore loadKeyStoreFromResource(String path, String password, String type) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            
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
}
