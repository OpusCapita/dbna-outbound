package com.opuscapita.dbna.outbound.config;

import com.helger.scope.IGlobalScope;
import com.helger.scope.mgr.ScopeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the ph-commons global scope required by Phase4 library.
 *
 * This configuration attempts to set up the global scope that Phase4 (via MetaAS4Manager)
 * requires for managing PMode and other AS4 configuration data structures.
 * If initialization fails, Phase4 will attempt to handle scope setup internally.
 */
@Configuration
public class ScopeInitializationConfig {
    private static final Logger logger = LoggerFactory.getLogger(ScopeInitializationConfig.class);

    /**
     * Initialize the global scope during application startup.
     * This bean ensures the scope is available for Phase4 if possible.
     * If scope creation fails, Phase4 may handle it internally.
     */
    @Bean
    public Object globalScopeInitializer() {
        logger.info("Checking ph-commons global scope for Phase4");

        try {
            // First check if a global scope already exists
            try {
                ScopeManager.getGlobalScope();
                logger.info("Global scope already initialized");
                return new Object();
            } catch (IllegalStateException ex) {
                // No scope exists - attempt to create one
                logger.debug("No global scope found, Phase4 may initialize it when needed");
            }

            // Attempt to create and set a global scope using reflection
            // This is a best-effort attempt - if it fails, Phase4 will handle it internally
            tryInitializeGlobalScope();

        } catch (Exception e) {
            logger.debug("Scope initialization attempt completed (Phase4 may handle internally)");
        }

        return new Object();
    }

    /**
     * Try to initialize the global scope using available constructors.
     * This uses reflection to find and instantiate appropriate scope classes.
     */
    private void tryInitializeGlobalScope() {
        // Try multiple known GlobalScope implementations
        String[] scopeClasses = {
            "com.helger.scope.singleton.GlobalScope",
            "com.helger.scope.impl.GlobalScope",
            "com.helger.scope.GlobalScope"
        };

        for (String className : scopeClasses) {
            try {
                Class<?> scopeClass = Class.forName(className);
                Object instance = scopeClass.getDeclaredConstructor().newInstance();
                if (instance instanceof IGlobalScope) {
                    ScopeManager.setGlobalScope((IGlobalScope) instance);
                    logger.info("Global scope initialized from class: {}", className);
                    return;
                }
            } catch (ClassNotFoundException ex) {
                // Class not found, try next one
            } catch (NoSuchMethodException ex) {
                // No default constructor, try next one
            } catch (Exception ex) {
                logger.debug("Could not initialize scope from class {}: {}", className, ex.getMessage());
            }
        }

        // If all attempts failed, log it but don't fail startup
        logger.debug("Could not initialize global scope via reflection. Phase4 will attempt internal initialization if needed.");
    }
}






