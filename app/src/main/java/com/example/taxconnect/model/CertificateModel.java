package com.example.taxconnect.model;

import java.io.Serializable;

public class CertificateModel implements Serializable {
    private String id;
    private String name;
    private String url;
    private String type; // "image" or "pdf"
    private long timestamp;

    public CertificateModel() {}

    public CertificateModel(String id, String name, String url, String type, long timestamp) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
