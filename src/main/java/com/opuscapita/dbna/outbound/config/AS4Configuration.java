package com.opuscapita.dbna.outbound.config;
import com.helger.phase4.crypto.AS4CryptoFactoryProperties;
import com.helger.phase4.crypto.AS4CryptoProperties;
import com.helger.phase4.crypto.IAS4CryptoFactory;
import com.helger.security.keystore.EKeyStoreType;
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
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
/**
 * Configuration for AS4 protocol with X.509 certificate support
 */
@Configuration
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
    @Value("${as4.truststore.path:truststore.jks}")
    private String truststorePath;
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
    @Bean
    public IAS4CryptoFactory as4CryptoFactory() {
        logger.info("Initializing AS4 crypto factory with X.509 certificate support");
        AS4CryptoProperties cryptoProps = new AS4CryptoProperties();
        File keystoreFile = new File(keystorePath);
        if (keystoreFile.exists()) {
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
        File truststoreFile = new File(truststorePath);
        if (truststoreFile.exists()) {
            logger.info("Loading truststore from: {}", truststorePath);
            cryptoProps.setTrustStorePath(truststorePath);
            cryptoProps.setTrustStorePassword(truststorePassword);
            cryptoProps.setTrustStoreType(EKeyStoreType.getFromIDCaseInsensitiveOrDefault(truststoreType, EKeyStoreType.JKS));
            logger.info("Truststore loaded successfully");
        } else {
            logger.warn("Truststore file not found at: {}. Certificate verification may fail.", truststorePath);
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
            KeyStore keyStore = null;
            File keystoreFile = new File(keystorePath);
            if (keystoreFile.exists()) {
                keyStore = KeyStore.getInstance(keystoreType);
                try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                    keyStore.load(fis, keystorePassword.toCharArray());
                }
                logger.info("Keystore loaded for SSL client authentication");
            }
            KeyStore trustStore = null;
            File truststoreFile = new File(truststorePath);
            if (truststoreFile.exists()) {
                trustStore = KeyStore.getInstance(truststoreType);
                try (FileInputStream fis = new FileInputStream(truststoreFile)) {
                    trustStore.load(fis, truststorePassword.toCharArray());
                }
                logger.info("Truststore loaded for SSL certificate verification");
            }
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.setProtocol(sslProtocol);
            if (keyStore != null) {
                sslContextBuilder.loadKeyMaterial(keyStore, keyPassword.toCharArray());
            }
            if (trustStore != null) {
                sslContextBuilder.loadTrustMaterial(trustStore, null);
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
    public String getKeystorePath() {
        return keystorePath;
    }
    public String getKeyAlias() {
        return keyAlias;
    }
    public boolean isKeystoreConfigured() {
        return new File(keystorePath).exists();
    }
    public boolean isTruststoreConfigured() {
        return new File(truststorePath).exists();
    }
}
