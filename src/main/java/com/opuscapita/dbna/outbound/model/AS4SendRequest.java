package com.opuscapita.dbna.outbound.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for sending AS4 messages
 * Per DBNA spec:
 * - We use receiver's certificate (from SMP) for local encryption only
 * - We sign with our certificate
 * - Receiver validates our signature using our certificate from SMP
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
    private String service;

    // Optional fields
    private String agreementRef;
    private boolean signMessage;
    private boolean encryptMessage;
}

