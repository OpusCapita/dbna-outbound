package com.opuscapita.dbna.outbound.controller;

import com.opuscapita.dbna.outbound.model.AS4SendRequest;
import com.opuscapita.dbna.outbound.model.AS4SendResponse;
import com.opuscapita.dbna.outbound.service.AS4SendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for sending UBL documents via AS4
 */
@RestController
@RequestMapping("/as4")
public class AS4SendController {
    
    private static final Logger logger = LoggerFactory.getLogger(AS4SendController.class);
    
    @Autowired
    private AS4SendService as4SendService;
    
    /**
     * Send UBL document via AS4 protocol
     */
    @PostMapping("/send/{senderId}/{receiverId}/{docTypeId}/{processId}")
    public ResponseEntity<AS4SendResponse> sendDocument(
            @PathVariable String senderId,
            @PathVariable String receiverId,
            @PathVariable String docTypeId,
            @PathVariable String processId,
            @RequestBody String documentContent) {
        logger.info("Received request to send UBL document - SenderId: {}, ReceiverId: {}, DocTypeId: {}, ProcessId: {}", 
            senderId, receiverId, docTypeId, processId);

        // Validate document content
        if (documentContent == null || documentContent.isEmpty()) {
            AS4SendResponse errorResponse = AS4SendResponse.builder()
                .success(false)
                .status("VALIDATION_ERROR")
                .errorMessage("Document content is required")
                .timestamp(System.currentTimeMillis())
                .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        // Build AS4SendRequest from path variables and document content
        AS4SendRequest request = AS4SendRequest.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .documentType(docTypeId)
                .processId(processId)
                .ublDocumentContent(documentContent)
                .build();
        
        // Send document
        AS4SendResponse response = as4SendService.sendAS4Message(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AS4 Outbound Service is running");
    }
}

