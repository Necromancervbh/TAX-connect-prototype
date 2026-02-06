package com.example.taxconnect.model;

import java.io.Serializable;

public class RatingModel implements Serializable {
    private String userId;
    private String userName;
    private String caId;
    private float rating;
    private String review;
    private long timestamp;

    public RatingModel() {
        // Required for Firestore
    }

    public RatingModel(String userId, String userName, String caId, float rating, String review, long timestamp) {
        this.userId = userId;
        this.userName = userName;
        this.caId = caId;
        this.rating = rating;
        this.review = review;
        this.timestamp = timestamp;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getCaId() { return caId; }
    public void setCaId(String caId) { this.caId = caId; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public String getReview() { return review; }
    public void setReview(String review) { this.review = review; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
