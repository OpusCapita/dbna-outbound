plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.opuscapita.dbna.outbound"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenLocal()
    mavenCentral()
    // Nexus repository - only added if credentials are available (optional for CI/Docker builds)
    if (extra.has("MAVEN_REGISTRY_URL")) {
        maven {
            name = "nexus"
            url = uri(extra["MAVEN_REGISTRY_URL"] as String)
            credentials {
                username = extra["MAVEN_REGISTRY_USER"] as String
                password = extra["MAVEN_REGISTRY_PASS"] as String
            }
        }
    }
}

extra["springCloudVersion"] = "2024.0.0"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Boot Actuator for monitoring and management
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Spring Retry for resilience patterns
    implementation("org.springframework.retry:spring-retry")
    
    // Spring Cloud Config
    implementation("org.springframework.cloud:spring-cloud-context")
    
    // AS4 Support - Phase4 library configured for DBNA network
    // The phase4-lib provides AS4 messaging capabilities that can be configured
    // for DBNA Alliance network communication (no separate DBNA client needed)
    // Note: phase4-lib brings in Apache HttpClient 5 transitively
    implementation("com.helger.phase4:phase4-lib:2.5.0")

    // UBL 2.3 Support
    implementation("com.helger.ubl:ph-ubl23:8.0.2")
    implementation("com.helger.ubl:ph-ubl23-codelists:8.0.2")
    
    // XML Processing - using versions compatible with phase4
    implementation("com.helger.commons:ph-commons:11.1.5")
    

    // Apache Commons Lang3 for utility functions
    implementation("org.apache.commons:commons-lang3:3.18.0")
    
    // Apache Commons IO for file utilities
    implementation("commons-io:commons-io:2.15.1")
    
    // BouncyCastle for X.509 certificate generation (testing/dummy certificates)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")
    
    // Apache HttpClient 5 - explicitly declared for SSL/TLS support
    implementation("org.apache.httpcomponents.client5:httpclient5")
    
    // Apache HttpClient 4 - for backward compatibility with dbna-common code
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    
    // JetBrains annotations for @NotNull, @Nullable, etc.
    implementation("org.jetbrains:annotations:24.1.0")

    // Lombok for cleaner code
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    
    // JSON Processing with Gson
    implementation("com.google.code.gson:gson:2.11.0")
    
    // Peppol support
    implementation("network.oxalis:oxalis-commons:5.0.5")
    implementation("network.oxalis.vefa:peppol-sbdh:2.0.2")
    
    // Google Guava for utilities (used by SMLLookupService)
    implementation("com.google.guava:guava:33.0.0-jre")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${extra["springCloudVersion"]}")
    }
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate files in bootJar
tasks.bootJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

