package com.example.taxconnect.model;

import java.util.List;

public class ConversationModel {
    private String conversationId;
    private List<String> participantIds; // [uid1, uid2]
    private String lastMessage;
    private long lastMessageTimestamp;
    private String otherUserName; // Cached name for display
    private String otherUserEmail; // Cached email
    private String otherUserProfileImage; // Cached profile image
    
    // Workflow States
    public static final String STATE_REQUESTED = "Requested";
    public static final String STATE_REFUSED = "Refused";
    public static final String STATE_DISCUSSION = "Discussion";
    public static final String STATE_PRICE_NEGOTIATION = "Price Negotiation";
    public static final String STATE_PRICE_AGREEMENT = "Price Agreement";
    public static final String STATE_ADVANCE_PAYMENT = "Advance Payment";
    public static final String STATE_DOCS_REQUEST = "Document Request";
    public static final String STATE_COMPLETED = "Completed";

    private String workflowState;
    private boolean videoCallAllowed = false; // Default disabled
    private String callStatus; // ACTIVE, REJECTED, ENDED

    public ConversationModel() {
        this.workflowState = STATE_DISCUSSION;
    }

    public ConversationModel(String conversationId, List<String> participantIds, String lastMessage, long lastMessageTimestamp, String otherUserName, String otherUserEmail) {
        this.conversationId = conversationId;
        this.participantIds = participantIds;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.otherUserName = otherUserName;
        this.otherUserEmail = otherUserEmail;
        this.workflowState = STATE_DISCUSSION;
    }

    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }

    public boolean isVideoCallAllowed() { return videoCallAllowed; }
    public void setVideoCallAllowed(boolean videoCallAllowed) { this.videoCallAllowed = videoCallAllowed; }

    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(long lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }
    
    public String getOtherUserName() { return otherUserName; }
    public void setOtherUserName(String otherUserName) { this.otherUserName = otherUserName; }

    public String getOtherUserEmail() { return otherUserEmail; }
    public void setOtherUserEmail(String otherUserEmail) { this.otherUserEmail = otherUserEmail; }

    public String getOtherUserProfileImage() { return otherUserProfileImage; }
    public void setOtherUserProfileImage(String otherUserProfileImage) { this.otherUserProfileImage = otherUserProfileImage; }
}
