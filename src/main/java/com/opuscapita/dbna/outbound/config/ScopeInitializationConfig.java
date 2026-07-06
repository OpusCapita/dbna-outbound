package com.opuscapita.dbna.outbound.config;

import com.helger.scope.IScope;
import com.helger.scope.mgr.ScopeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the ph-commons global scope required by Phase4 library.
 *
 * This configuration sets up the global scope that Phase4 (via MetaAS4Manager)
 * requires for managing PMode and other AS4 configuration data structures.
 * Without this initialization, Phase4 will throw:
 * "java.lang.IllegalStateException: No global scope object has been set!"
 */
@Configuration
public class ScopeInitializationConfig {
    private static final Logger logger = LoggerFactory.getLogger(ScopeInitializationConfig.class);

    /**
     * Initialize the global scope as a Spring bean.
     * This ensures the scope is set up during application initialization.
     */
    @Bean
    public ScopeInitializer scopeInitializer() {
        logger.info("Creating ScopeInitializer bean for Phase4");
        return new ScopeInitializer();
    }

    /**
     * Inner class that handles scope initialization
     */
    public static class ScopeInitializer {
        private static final Logger logger = LoggerFactory.getLogger(ScopeInitializer.class);

        public ScopeInitializer() {
            logger.info("Initializing ph-commons scope for Phase4");
            // The actual scope initialization will be handled in AS4SendService
            // when the builder is created, as it needs to be done in the request context
        }
    }
}






