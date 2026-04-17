package com.opuscapita.dbna.outbound.service;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for querying DBNA Service Metadata Locator (SML) via DNS
 * 
 * According to DBNA SML Profile v1.2:
 * - Constructs DNS names using SHA256 hash and Base32 encoding of party identifiers
 * - Queries NAPTR DNS records to discover SMP service endpoints
 * - Supports production (sml.dbnalliance.net), test (sml.dbnalliance.com), and pilot environments
 */
@Service
public class SMLLookupService {
    private static final Logger logger = LoggerFactory.getLogger(SMLLookupService.class);
    
    private static final String SML_PRODUCTION = "sml.dbnalliance.net";
    private static final String SML_TEST = "sml.dbnalliance.com";
    private static final String SML_PILOT = "sml.dbnalliancepilot.net";
    private static final String NAPTR_SERVICE_TYPE = "oasis-bdxr-smp-2#dbnalliance-1.1";
    private static final Pattern SMP_URL_PATTERN = Pattern.compile("!\\^.*\\$!([^!]+)!");
    
    @Value("${dbna.sml.environment:production}")
    private String smlEnvironment;
    
    @Value("${dbna.sml.dns-server:8.8.8.8}")
    private String dnsServer;
    
    /**
     * Looks up the SMP service endpoint for a given party identifier
     * 
     * @param identifierScheme The identifier scheme (e.g., "GLN", "0192")
     * @param partyIdentifier The party identifier
     * @return The SMP service endpoint URL, or null if not found
     * @throws Exception if the lookup fails
     */
    public String lookupSMPEndpoint(String identifierScheme, String partyIdentifier) throws Exception {
        if (identifierScheme == null || identifierScheme.trim().isEmpty()) {
            throw new IllegalArgumentException("Identifier scheme is required");
        }
        if (partyIdentifier == null || partyIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Party identifier is required");
        }
        
        logger.info("Looking up SMP endpoint for scheme: {}, identifier: {}", identifierScheme, partyIdentifier);
        
        try {
            // Step 1: Construct the DNS name using DBNA SML Profile algorithm
            String dnsName = constructDNSName(identifierScheme, partyIdentifier);
            logger.debug("Constructed DNS name for SML query: {}", dnsName);
            
            // Step 2: Query NAPTR records from DNS
            String smpUrl = queryNAPTRRecord(dnsName);
            
            if (smpUrl != null) {
                logger.info("Successfully resolved SMP endpoint: {}", smpUrl);
                return smpUrl;
            } else {
                logger.warn("No SMP endpoint found for scheme: {}, identifier: {}", identifierScheme, partyIdentifier);
                return null;
            }
        } catch (NamingException e) {
            logger.error("DNS lookup failed for party: {}::{}", identifierScheme, partyIdentifier, e);
            throw new Exception("SML lookup failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error during SML lookup", e);
            throw e;
        }
    }
    
    /**
     * Constructs the DNS name according to DBNA SML Profile v1.2
     * 
     * Algorithm:
     * 1. Concatenate scheme and identifier with "::" delimiter
     * 2. Compute SHA256 hash of the lowercased string
     * 3. Base32 encode the digest
     * 4. Remove trailing "=" characters
     * 5. Append to SML domain name
     */
    private String constructDNSName(String scheme, String identifier) {
        String concatenated = scheme.toLowerCase() + "::" + identifier.toLowerCase();
        logger.debug("Concatenated party identifier: {}", concatenated);
        
        // Compute SHA256 hash
        byte[] digest = Hashing.sha256()
            .hashString(concatenated, StandardCharsets.UTF_8)
            .asBytes();
        
        // Base32 encode and remove trailing "="
        String encoded = BaseEncoding.base32()
            .encode(digest)
            .toLowerCase()
            .replaceAll("=+$", "");
        
        logger.debug("Base32 encoded and stripped: {}", encoded);
        
        String smlDomain = getSMLDomain();
        String dnsName = encoded + "." + smlDomain;
        
        logger.debug("Final DNS name for NAPTR query: {}", dnsName);
        return dnsName;
    }
    
    /**
     * Queries NAPTR records from DNS
     * 
     * Returns the URL from the reg.exp. field of the NAPTR record matching the DBNA service type
     */
    private String queryNAPTRRecord(String dnsName) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns://" + dnsServer);
        
        DirContext dirContext = new InitialDirContext(env);
        
        try {
            logger.debug("Querying NAPTR records for: {}", dnsName);
            Attributes attributes = dirContext.getAttributes(dnsName, new String[]{"NAPTR"});
            
            Attribute naptr = attributes.get("NAPTR");
            if (naptr == null) {
                logger.warn("No NAPTR records found for: {}", dnsName);
                return null;
            }
            
            NamingEnumeration<?> records = naptr.getAll();
            while (records.hasMore()) {
                String record = (String) records.next();
                logger.debug("Found NAPTR record: {}", record);
                
                // Check if this is the DBNA SMP service record
                if (record.contains(NAPTR_SERVICE_TYPE)) {
                    // Extract the SMP URL from the reg.exp. field
                    String smpUrl = extractSMPUrl(record);
                    if (smpUrl != null) {
                        logger.debug("Extracted SMP URL from NAPTR record: {}", smpUrl);
                        return smpUrl;
                    }
                }
            }
            
            logger.warn("No NAPTR record with service type {} found", NAPTR_SERVICE_TYPE);
            return null;
        } catch (javax.naming.NameNotFoundException e) {
            // DNS name not found - party is not registered in SML
            logger.warn("Party not found in SML - DNS name not found: {}", dnsName);
            throw new NamingException("Party identifier not found in SML registry: " + e.getMessage());
        } catch (javax.naming.ServiceUnavailableException e) {
            // DNS service unavailable
            logger.warn("DNS service unavailable while querying: {}", dnsName);
            throw new NamingException("DNS service unavailable: " + e.getMessage());
        } catch (javax.naming.CommunicationException e) {
            // Network communication error
            logger.warn("DNS communication error while querying: {}", dnsName);
            throw new NamingException("DNS communication error: " + e.getMessage());
        } finally {
            dirContext.close();
        }
    }
    
    /**
     * Extracts the SMP URL from a NAPTR record
     * 
     * Expected format: 100 10 "U" "oasis-bdxr-smp-2#dbnalliance-1.1" "!^.*$!https://smp.example.com/service/!" .
     */
    private String extractSMPUrl(String naptyRecord) {
        Matcher matcher = SMP_URL_PATTERN.matcher(naptyRecord);
        if (matcher.find()) {
            String url = matcher.group(1).trim();
            logger.debug("Extracted SMP URL: {}", url);
            return url;
        }
        logger.warn("Could not extract SMP URL from NAPTR record: {}", naptyRecord);
        return null;
    }
    
    /**
     * Returns the SML domain based on the configured environment
     */
    private String getSMLDomain() {
        return switch (smlEnvironment.toLowerCase()) {
            case "test" -> {
                logger.debug("Using test SML environment: {}", SML_TEST);
                yield SML_TEST;
            }
            case "pilot" -> {
                logger.debug("Using pilot SML environment: {}", SML_PILOT);
                yield SML_PILOT;
            }
            case "production", "" -> {
                logger.debug("Using production SML environment: {}", SML_PRODUCTION);
                yield SML_PRODUCTION;
            }
            default -> {
                logger.debug("Using production SML environment: {}", SML_PRODUCTION);
                yield SML_PRODUCTION;
            }
        };
    }
}


