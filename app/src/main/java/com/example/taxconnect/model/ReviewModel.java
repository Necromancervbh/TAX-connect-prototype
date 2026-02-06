package com.example.taxconnect.model;

public class ReviewModel {
    private String reviewId;
    private String caId;
    private String customerId;
    private String customerName;
    private float rating;
    private String comment;
    private long timestamp;

    public ReviewModel() {}

    public ReviewModel(String reviewId, String caId, String customerId, String customerName, float rating, String comment, long timestamp) {
        this.reviewId = reviewId;
        this.caId = caId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.rating = rating;
        this.comment = comment;
        this.timestamp = timestamp;
    }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getCaId() { return caId; }
    public void setCaId(String caId) { this.caId = caId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
