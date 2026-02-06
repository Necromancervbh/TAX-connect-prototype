package com.example.taxconnect.model;

import java.io.Serializable;

public class BookingModel implements Serializable {
    private String id;
    private String caId;
    private String userId;
    private String userName; // User's name for CA to see
    private String caName; // CA's name for User to see
    private long appointmentTimestamp;
    private String status; // PENDING, CONFIRMED, COMPLETED, CANCELLED
    private long createdAt;
    private String message;

    public BookingModel() {}

    public BookingModel(String id, String caId, String userId, String userName, String caName, long appointmentTimestamp, String status, String message) {
        this.id = id;
        this.caId = caId;
        this.userId = userId;
        this.userName = userName;
        this.caName = caName;
        this.appointmentTimestamp = appointmentTimestamp;
        this.status = status;
        this.message = message;
        this.createdAt = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCaId() { return caId; }
    public void setCaId(String caId) { this.caId = caId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getCaName() { return caName; }
    public void setCaName(String caName) { this.caName = caName; }

    public long getAppointmentTimestamp() { return appointmentTimestamp; }
    public void setAppointmentTimestamp(long appointmentTimestamp) { this.appointmentTimestamp = appointmentTimestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
