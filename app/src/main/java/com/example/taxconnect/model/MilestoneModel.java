package com.example.taxconnect.model;

import com.google.firebase.Timestamp;

public class MilestoneModel {
    private String id;
    private String bookingId;
    private String title;
    private String description;
    private String status; // PENDING, IN_PROGRESS, COMPLETED
    private Timestamp timestamp;

    public MilestoneModel() {}

    public MilestoneModel(String id, String bookingId, String title, String description, String status, Timestamp timestamp) {
        this.id = id;
        this.bookingId = bookingId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
