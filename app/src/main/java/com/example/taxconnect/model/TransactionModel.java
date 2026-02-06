package com.example.taxconnect.model;

import com.google.firebase.Timestamp;

public class TransactionModel {
    private String transactionId;
    private String userId;
    private String caId;
    private String caName;
    private String description;
    private double amount;
    private String status; // "SUCCESS", "FAILED", "PENDING"
    private Timestamp timestamp;

    public TransactionModel() { }

    public TransactionModel(String transactionId, String userId, String caId, String caName, String description, double amount, String status) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.caId = caId;
        this.caName = caName;
        this.description = description;
        this.amount = amount;
        this.status = status;
        this.timestamp = Timestamp.now();
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCaId() { return caId; }
    public void setCaId(String caId) { this.caId = caId; }

    public String getCaName() { return caName; }
    public void setCaName(String caName) { this.caName = caName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
