package com.example.taxconnect.model;

import java.util.Date;
import java.io.Serializable;

public class CommentModel implements Serializable {
    private String id;
    private String postId;
    private String userId;
    private String userName;
    private String userRole;
    private String content;
    private Date timestamp;

    public CommentModel() {}

    public CommentModel(String id, String postId, String userId, String userName, String userRole, String content, Date timestamp) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.userRole = userRole;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

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
}
