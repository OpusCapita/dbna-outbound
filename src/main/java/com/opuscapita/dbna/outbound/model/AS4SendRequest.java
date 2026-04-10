package com.opuscapita.dbna.outbound.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for sending AS4 messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AS4SendRequest {
    
    private String receiverEndpointUrl;
    private String senderId;
    private String receiverId;
    private String conversationId;
    private String documentType;
    private String processId;
    private String ublDocumentContent;
    private String action;
    private String service;
    
    // Optional fields
    private String agreementRef;
    private boolean signMessage;
    private boolean encryptMessage;
}

