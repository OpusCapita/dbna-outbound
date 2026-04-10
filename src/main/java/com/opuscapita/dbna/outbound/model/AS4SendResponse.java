package com.opuscapita.dbna.outbound.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for AS4 send operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AS4SendResponse {
    
    private boolean success;
    private String messageId;
    private String status;
    private String errorMessage;
    private long timestamp;
}

