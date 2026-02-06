package com.example.taxconnect.model;

import com.google.firebase.Timestamp;

public class DocumentModel {
    private String id;
    private String name;
    private String url;
    private String type; // pdf, image, etc.
    private String category;
    private Timestamp uploadedAt;

    public DocumentModel() {
    }

    public DocumentModel(String id, String name, String url, String type, Timestamp uploadedAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.type = type;
        this.uploadedAt = uploadedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Timestamp uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
