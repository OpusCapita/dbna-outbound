package com.opuscapita.dbna.outbound.config;

import lombok.Getter;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.util.Base64;

@Configuration
@RefreshScope
public class SiriusConfiguration {

    private final static Logger logger = LoggerFactory.getLogger(SiriusConfiguration.class);

    @Getter
    @Value("${sirius.url:''}")
    private String url;

    @Value("${sirius.username:''}")
    private String username;

    @Value("${sirius.password:''}")
    private String password;

    @Getter
    @Value("${sirius.size-limit:5242880}")
    private Long sizeLimit;

    @Value("${sirius.timeout:180000}")
    private long timeout;

    @Getter
    @Value("${sirius.retry-count:8}")
    private int retryCount;

    @Getter
    @Value("${sirius.retry-delay:900000}")
    private int retryDelay;

    @PostConstruct
    public void logConfiguration() {
        logger.info("Sirius Configuration Initialized:");
        logger.info("  URL: {}", url);
        logger.info("  Timeout: {} ms ({} minutes)", timeout, timeout / 60000);
        logger.info("  Retry Count: {}", retryCount);
        logger.info("  Retry Delay: {} ms ({} minutes)", retryDelay, retryDelay / 60000);
        logger.info("  Size Limit: {} bytes ({} MB)", sizeLimit, sizeLimit / (1024 * 1024));
    }

    private RequestConfig getRequestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout))
                .setConnectTimeout(Timeout.ofMilliseconds(timeout))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout))
                .build();
    }

    private PoolingHttpClientConnectionManager getConnectionManager() {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(100);
        manager.setDefaultMaxPerRoute(100);
        return manager;
    }

    @Bean
    @Qualifier("siriusRestTemplate")
    public RestTemplate siriusRestTemplate() {
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(getConnectionManager())
                .setDefaultRequestConfig(getRequestConfig())
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(requestFactory);
    }

    public String getAuthHeader() {
        byte[] basicAuthValue = (username + ":" + password).getBytes();
        return "Basic " + Base64.getEncoder().encodeToString(basicAuthValue);
    }

}
