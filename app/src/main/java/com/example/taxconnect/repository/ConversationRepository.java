package com.example.taxconnect.repository;

import com.example.taxconnect.DataRepository;
import com.example.taxconnect.model.ConversationModel;
import com.example.taxconnect.model.MessageModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationRepository {

    private static ConversationRepository instance;
    private final FirebaseFirestore firestore;

    private ConversationRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized ConversationRepository getInstance() {
        if (instance == null) {
            instance = new ConversationRepository();
        }
        return instance;
    }

    private void updateFieldWithRetry(com.google.firebase.firestore.DocumentReference ref, String field, Object value, int retries, final DataRepository.DataCallback<Void> callback) {
        ref.update(field, value)
                .addOnSuccessListener(aVoid -> { if (callback != null) callback.onSuccess(null); })
                .addOnFailureListener(e -> {
                    if (retries > 0) {
                        updateFieldWithRetry(ref, field, value, retries - 1, callback);
                    } else {
                        if (callback != null) callback.onError(e.getMessage());
                    }
                });
    }

    public void updateCallStatus(String chatId, String status, final DataRepository.DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("conversations").document(chatId);
        updateFieldWithRetry(ref, "callStatus", status, 2, callback);
    }

    public ListenerRegistration listenToConversation(String chatId, final DataRepository.DataCallback<ConversationModel> callback) {
        return firestore.collection("conversations").document(chatId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        callback.onError(e.getMessage());
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        ConversationModel conversation = snapshot.toObject(ConversationModel.class);
                        callback.onSuccess(conversation);
                    } else {
                        callback.onSuccess(null);
                    }
                });
    }

    public ListenerRegistration listenForRecentMessages(String chatId, int limit, final DataRepository.DataCallback<List<MessageModel>> callback) {
        return firestore.collection("conversations").document(chatId).collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .limitToLast(limit)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e.getMessage());
                        return;
                    }
                    List<MessageModel> messages = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            MessageModel msg = doc.toObject(MessageModel.class);
                            msg.setId(doc.getId());
                            messages.add(msg);
                        }
                    }
                    callback.onSuccess(messages);
                });
    }

    public void getMessagesPage(String chatId, int limit, DocumentSnapshot lastSnapshot, final DataRepository.DataCallback<DataRepository.PageResult<MessageModel>> callback) {
        Query query = firestore.collection("conversations").document(chatId).collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<MessageModel> messages = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        MessageModel msg = doc.toObject(MessageModel.class);
                        msg.setId(doc.getId());
                        messages.add(msg);
                    }
                    java.util.Collections.reverse(messages);
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new DataRepository.PageResult<>(messages, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateConversationState(String chatId, String state, final DataRepository.DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("conversations").document(chatId);
        updateFieldWithRetry(ref, "workflowState", state, 2, callback);
    }

    public void updateVideoCallPermission(String chatId, boolean allowed, final DataRepository.DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("conversations").document(chatId);
        updateFieldWithRetry(ref, "videoCallAllowed", allowed, 2, callback);
    }

    public String getChatId(String uid1, String uid2) {
        if (uid1.compareTo(uid2) < 0) {
            return uid1 + "_" + uid2;
        } else {
            return uid2 + "_" + uid1;
        }
    }

    public void sendMessage(MessageModel message, final DataRepository.DataCallback<Void> callback) {
        String chatId = getChatId(message.getSenderId(), message.getReceiverId());
        message.setChatId(chatId);
        firestore.collection("conversations").document(chatId).collection("messages")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    updateConversation(message);
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void updateConversation(MessageModel message) {
        String chatId = message.getChatId();
        List<String> participants = Arrays.asList(message.getSenderId(), message.getReceiverId());
        Map<String, Object> updates = new HashMap<>();
        updates.put("conversationId", chatId);
        updates.put("participantIds", participants);
        String lastMsg = message.getMessage();
        if ("DOCUMENT".equals(message.getType())) {
            lastMsg = "📄 " + (lastMsg != null ? lastMsg : "Document");
        } else if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
            lastMsg = "📷 Image";
        } else if ("PROPOSAL".equals(message.getType())) {
            lastMsg = "Proposal: " + message.getProposalDescription();
        }
        updates.put("lastMessage", lastMsg);
        updates.put("lastMessageTimestamp", message.getTimestamp());
        firestore.collection("conversations").document(chatId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge());
    }

    public void sendRequest(String senderId, String receiverId, String initialMessageText, final DataRepository.DataCallback<Void> callback) {
        String chatId = getChatId(senderId, receiverId);
        firestore.collection("conversations").document(chatId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        ConversationModel existing = snapshot.toObject(ConversationModel.class);
                        if (existing != null && (ConversationModel.STATE_REFUSED.equals(existing.getWorkflowState()) || existing.getWorkflowState() == null)) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("workflowState", ConversationModel.STATE_REQUESTED);
                            updates.put("lastMessage", initialMessageText);
                            updates.put("lastMessageTimestamp", System.currentTimeMillis());
                            firestore.collection("conversations").document(chatId).update(updates)
                                    .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
                        } else {
                            if (ConversationModel.STATE_REQUESTED.equals(existing.getWorkflowState())) {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("lastMessage", initialMessageText);
                                updates.put("lastMessageTimestamp", System.currentTimeMillis());
                                firestore.collection("conversations").document(chatId).update(updates)
                                        .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
                            } else {
                                sendMessage(new MessageModel(senderId, receiverId, chatId, initialMessageText, System.currentTimeMillis(), "TEXT"), callback);
                            }
                        }
                    } else {
                        ConversationModel newConv = new ConversationModel();
                        newConv.setConversationId(chatId);
                        newConv.setParticipantIds(Arrays.asList(senderId, receiverId));
                        newConv.setWorkflowState(ConversationModel.STATE_REQUESTED);
                        newConv.setLastMessage(initialMessageText);
                        newConv.setLastMessageTimestamp(System.currentTimeMillis());
                        firestore.collection("conversations").document(chatId).set(newConv)
                                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getConversations(String userId, final DataRepository.DataCallback<List<ConversationModel>> callback) {
        firestore.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .orderBy("lastMessageTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e.getMessage());
                        return;
                    }
                    List<ConversationModel> conversations = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ConversationModel conv = doc.toObject(ConversationModel.class);
                            if (conv != null && !ConversationModel.STATE_REQUESTED.equals(conv.getWorkflowState()) &&
                                    !ConversationModel.STATE_REFUSED.equals(conv.getWorkflowState())) {
                                conversations.add(conv);
                            }
                        }
                    }
                    if (conversations.isEmpty()) {
                        callback.onSuccess(conversations);
                        return;
                    }
                    populateUserDetails(userId, conversations, callback);
                });
    }

    private void populateUserDetails(String userId, List<ConversationModel> conversations, final DataRepository.DataCallback<List<ConversationModel>> callback) {
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (ConversationModel conv : conversations) {
            String otherId = null;
            if (conv.getParticipantIds() != null) {
                for (String pid : conv.getParticipantIds()) {
                    if (!pid.equals(userId)) {
                        otherId = pid;
                        break;
                    }
                }
            }
            if (otherId != null) {
                tasks.add(firestore.collection("users").document(otherId).get());
            }
        }
        if (tasks.isEmpty()) {
            callback.onSuccess(conversations);
            return;
        }
        Tasks.whenAllSuccess(tasks).addOnSuccessListener(objects -> {
            for (Object obj : objects) {
                DocumentSnapshot userDoc = (DocumentSnapshot) obj;
                if (userDoc.exists()) {
                    com.example.taxconnect.model.UserModel user = userDoc.toObject(com.example.taxconnect.model.UserModel.class);
                    if (user != null) {
                        for (ConversationModel conv : conversations) {
                            if (conv.getParticipantIds().contains(user.getUid())) {
                                conv.setOtherUserName(user.getName());
                                conv.setOtherUserEmail(user.getEmail());
                                conv.setOtherUserProfileImage(user.getProfileImageUrl());
                            }
                        }
                    }
                }
            }
            callback.onSuccess(conversations);
        }).addOnFailureListener(err -> {
            callback.onSuccess(conversations);
        });
    }
}
