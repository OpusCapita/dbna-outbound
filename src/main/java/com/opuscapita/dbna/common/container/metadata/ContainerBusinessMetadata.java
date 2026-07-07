package com.opuscapita.dbna.common.container.metadata;

public class ContainerBusinessMetadata {

    private String documentId;
    private String issueDate;
    private String issueTime;
    private String senderName;
    private String receiverName;

    public ContainerBusinessMetadata() {
    }

    public ContainerBusinessMetadata(String id, String date, String time, String sender, String receiver) {
        this.documentId = id;
        this.issueDate = date;
        this.issueTime = time;
        this.senderName = sender;
        this.receiverName = receiver;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

    public String getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(String issueTime) {
        this.issueTime = issueTime;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }
}

