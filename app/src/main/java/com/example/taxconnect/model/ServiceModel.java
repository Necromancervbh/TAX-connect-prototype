package com.example.taxconnect.model;

import java.io.Serializable;

public class ServiceModel implements Serializable {
    private String id;
    private String caId;
    private String title;
    private String description;
    private String price;
    private String estimatedTime; // e.g., "2 Days"

    public ServiceModel() {}

    public ServiceModel(String id, String caId, String title, String description, String price, String estimatedTime) {
        this.id = id;
        this.caId = caId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.estimatedTime = estimatedTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCaId() { return caId; }
    public void setCaId(String caId) { this.caId = caId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }
}
