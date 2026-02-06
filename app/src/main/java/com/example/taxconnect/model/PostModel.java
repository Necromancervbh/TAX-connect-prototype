package com.example.taxconnect.model;

import java.util.Date;
import java.util.List;
import java.io.Serializable;

public class PostModel implements Serializable {
    private String id;
    private String userId;
    private String userName;
    private String userRole; // "Client" or "CA"
    private String content;
    private Date timestamp;
    private int likeCount;
    private int commentCount;
    private List<String> tags;
    private List<String> likedBy;

    public PostModel() {}

    public PostModel(String id, String userId, String userName, String userRole, String content, Date timestamp) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userRole = userRole;
        this.content = content;
        this.timestamp = timestamp;
        this.likeCount = 0;
        this.commentCount = 0;
        this.likedBy = new java.util.ArrayList<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getLikedBy() { return likedBy; }
    public void setLikedBy(List<String> likedBy) { this.likedBy = likedBy; }
}
