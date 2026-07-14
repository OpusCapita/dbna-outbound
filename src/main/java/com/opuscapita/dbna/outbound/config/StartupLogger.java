package com.opuscapita.dbna.outbound.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup event listener that logs application version information.
 * Displays the application version at startup.
 */
@Slf4j
@Component
public class StartupLogger implements CommandLineRunner {

    @Value("${app.version:unknown}")
    private String appVersion;

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("DBNA Inbound AS4 Access Point Started");
        log.info("Application Version: {}", appVersion);
        log.info("========================================");
    }
}

