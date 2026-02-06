package com.example.taxconnect;

import android.content.Context;

import com.example.taxconnect.model.ConversationModel;
import com.example.taxconnect.model.MessageModel;
import com.example.taxconnect.model.TransactionModel;
import com.example.taxconnect.model.UserModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository class to handle backend logic.
 * Integrates Firebase (Auth/Firestore), Cloudinary, and Agora.
 */
public class DataRepository {

    private static DataRepository instance;
    
    // Firebase
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final java.util.Map<String, CacheEntry> cache = new java.util.HashMap<>();

    private DataRepository() {
        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized DataRepository getInstance() {
        if (instance == null) {
            instance = new DataRepository();
        }
        return instance;
    }

    private void updateFieldWithRetry(com.google.firebase.firestore.DocumentReference ref, String field, Object value, int retries, final DataCallback<Void> callback) {
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

    private static class CacheEntry {
        final Object data;
        final long ts;
        CacheEntry(Object d, long t) { this.data = d; this.ts = t; }
    }

    private boolean isCacheValid(String key, long ttlMs) {
        CacheEntry ce = cache.get(key);
        return ce != null && (System.currentTimeMillis() - ce.ts) < ttlMs;
    }

    private <T> void respondFromCache(String key, DataCallback<T> callback) {
        CacheEntry ce = cache.get(key);
        if (ce != null) {
            @SuppressWarnings("unchecked")
            T data = (T) ce.data;
            callback.onSuccess(data);
        } else {
            callback.onError("Cache miss");
        }
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    public static class PageResult<T> {
        private final List<T> items;
        private final DocumentSnapshot lastSnapshot;

        public PageResult(List<T> items, DocumentSnapshot lastSnapshot) {
            this.items = items;
            this.lastSnapshot = lastSnapshot;
        }

        public List<T> getItems() {
            return items;
        }

        public DocumentSnapshot getLastSnapshot() {
            return lastSnapshot;
        }
    }

    // --- Services ---

    public void saveService(com.example.taxconnect.model.ServiceModel service, final DataCallback<Void> callback) {
        firestore.collection("services").document(service.getId()).set(service)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateCallStatus(String chatId, String status, final DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("conversations").document(chatId);
        updateFieldWithRetry(ref, "callStatus", status, 2, callback);
    }

    public void getServices(String caId, final DataCallback<List<com.example.taxconnect.model.ServiceModel>> callback) {
        firestore.collection("services").whereEqualTo("caId", caId).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.ServiceModel> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        list.add(doc.toObject(com.example.taxconnect.model.ServiceModel.class));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void deleteService(String serviceId, final DataCallback<Void> callback) {
        firestore.collection("services").document(serviceId).delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Documents ---

    public void saveDocument(String userId, com.example.taxconnect.model.DocumentModel doc, final DataCallback<Boolean> callback) {
        firestore.collection("users").document(userId).collection("documents").document(doc.getId()).set(doc)
                .addOnSuccessListener(aVoid -> callback.onSuccess(true))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getDocuments(String userId, final DataCallback<List<com.example.taxconnect.model.DocumentModel>> callback) {
        firestore.collection("users").document(userId).collection("documents")
                .orderBy("uploadedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.DocumentModel> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        list.add(doc.toObject(com.example.taxconnect.model.DocumentModel.class));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Ratings ---

    public void addRating(com.example.taxconnect.model.RatingModel rating, final DataCallback<Void> callback) {
        String caId = rating.getCaId();
        String userId = rating.getUserId();

        firestore.runTransaction(transaction -> {
            // 1. Reference to CA User document
            com.google.firebase.firestore.DocumentReference caRef = firestore.collection("users").document(caId);
            DocumentSnapshot caSnapshot = transaction.get(caRef);

            // 2. Reference to User Rating document
            com.google.firebase.firestore.DocumentReference ratingRef = caRef.collection("ratings").document(userId);
            DocumentSnapshot ratingSnapshot = transaction.get(ratingRef);

            // 3. Logic to update rating
            double currentRating = 0.0;
            long currentCount = 0;

            if (caSnapshot.exists()) {
                Double r = caSnapshot.getDouble("rating");
                Long c = caSnapshot.getLong("ratingCount");
                if (r != null) currentRating = r;
                if (c != null) currentCount = c;
            }

            double newRatingVal = rating.getRating();
            double newAverage;
            long newCount;

            if (ratingSnapshot.exists()) {
                // Update existing rating
                double oldRatingVal = 0.0;
                Double oldR = ratingSnapshot.getDouble("rating");
                if (oldR != null) oldRatingVal = oldR;

                // Remove old contribution and add new
                double totalScore = (currentRating * currentCount) - oldRatingVal + newRatingVal;
                newCount = currentCount; // Count remains same
                newAverage = totalScore / newCount;

            } else {
                // New rating
                double totalScore = (currentRating * currentCount) + newRatingVal;
                newCount = currentCount + 1;
                newAverage = totalScore / newCount;
            }

            // 4. Write updates
            transaction.set(ratingRef, rating);
            transaction.update(caRef, "rating", newAverage, "ratingCount", newCount);

            return null;
        }).addOnSuccessListener(aVoid -> callback.onSuccess(null))
        .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getRatings(String caId, final DataCallback<List<com.example.taxconnect.model.RatingModel>> callback) {
        firestore.collection("users").document(caId).collection("ratings")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.RatingModel> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        list.add(doc.toObject(com.example.taxconnect.model.RatingModel.class));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Users ---

    public void saveUser(UserModel user, final DataCallback<Void> callback) {
        firestore.collection("users").document(user.getUid()).set(user)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void checkRatingEligibility(String userId, String caId, final DataCallback<Boolean> callback) {
        // Check if there is any conversation where state is NOT REQUESTED or REFUSED
        firestore.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean isEligible = false;
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        ConversationModel conv = doc.toObject(ConversationModel.class);
                        if (conv != null && conv.getParticipantIds().contains(caId)) {
                             String state = conv.getWorkflowState();
                             if (!ConversationModel.STATE_REQUESTED.equals(state) && 
                                 !ConversationModel.STATE_REFUSED.equals(state)) {
                                 isEligible = true;
                                 break;
                             }
                        }
                    }
                    callback.onSuccess(isEligible);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Authentication ---

    public void loginUser(String email, String password, final DataCallback<UserModel> callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = task.getResult().getUser().getUid();
                        fetchUser(uid, callback);
                    } else {
                        callback.onError(task.getException().getMessage());
                    }
                });
    }

    public void registerUser(String email, String password, UserModel userModel, final DataCallback<Void> callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = task.getResult().getUser().getUid();
                        userModel.setUid(uid);
                        
                        firestore.collection("users").document(uid).set(userModel)
                                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                .addOnFailureListener(e -> callback.onError("Firestore Error: " + e.getMessage()));
                    } else {
                        callback.onError("Auth Error: " + task.getException().getMessage());
                    }
                });
    }

    public void fetchUser(String uid, final DataCallback<UserModel> callback) {
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel user = documentSnapshot.toObject(UserModel.class);
                        callback.onSuccess(user);
                    } else {
                        callback.onError("User not found in database");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateUserStatus(String uid, boolean isOnline, final DataCallback<Void> callback) {
        firestore.collection("users").document(uid)
                .update("online", isOnline)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateUser(UserModel userModel, final DataCallback<Void> callback) {
        firestore.collection("users").document(userModel.getUid()).set(userModel)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("Firestore Error: " + e.getMessage()));
    }

    public void updateUserProfile(String uid, Map<String, Object> updates, final DataCallback<Void> callback) {
        firestore.collection("users").document(uid).update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateFcmToken(String token) {
        if (firebaseAuth.getCurrentUser() != null) {
            String uid = firebaseAuth.getCurrentUser().getUid();
            firestore.collection("users").document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener(e -> {
                        // Log error
                    });
        }
    }

    public void getFavoriteCaIds(String userId, final DataCallback<List<String>> callback) {
        firestore.collection("users").document(userId).collection("favorites")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        list.add(doc.getId());
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void toggleFavorite(String userId, String caId, final DataCallback<Boolean> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("users").document(userId).collection("favorites").document(caId);
        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                ref.delete().addOnSuccessListener(aVoid -> callback.onSuccess(false))
                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("timestamp", System.currentTimeMillis());
                data.put("caId", caId);
                ref.set(data).addOnSuccessListener(aVoid -> callback.onSuccess(true))
                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
            }
        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Data Fetching ---
    
    public void getCAList(final DataCallback<List<UserModel>> callback) {
        String cacheKey = "ca_list_page0";
        long ttl = 60_000L;
        if (isCacheValid(cacheKey, ttl)) {
            respondFromCache(cacheKey, new DataCallback<List<UserModel>>() {
                @Override public void onSuccess(List<UserModel> data) { callback.onSuccess(data); }
                @Override public void onError(String error) { }
            });
            return;
        }
        getCaPage(50, null, new DataCallback<PageResult<UserModel>>() {
            @Override
            public void onSuccess(PageResult<UserModel> data) {
                cache.put(cacheKey, new CacheEntry(data.getItems(), System.currentTimeMillis()));
                callback.onSuccess(data.getItems());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCaPage(int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<UserModel>> callback) {
        Query query = firestore.collection("users")
                .whereEqualTo("role", "CA")
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<UserModel> caList = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        UserModel user = document.toObject(UserModel.class);
                        caList.add(user);
                    }
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new PageResult<>(caList, lastDoc));
                })
                .addOnFailureListener(e -> {
                    Query fallback = firestore.collection("users")
                            .whereEqualTo("role", "CA")
                            .limit(limit);
                    if (lastSnapshot != null) {
                        fallback = fallback.startAfter(lastSnapshot);
                    }
                    fallback.get()
                            .addOnSuccessListener(q -> {
                                List<UserModel> caList = new ArrayList<>();
                                for (DocumentSnapshot document : q.getDocuments()) {
                                    UserModel user = document.toObject(UserModel.class);
                                    caList.add(user);
                                }
                                java.util.Collections.sort(caList, (o1, o2) -> Double.compare(o2.getRating(), o1.getRating()));
                                DocumentSnapshot lastDoc = q.getDocuments().isEmpty()
                                        ? null
                                        : q.getDocuments().get(q.getDocuments().size() - 1);
                                callback.onSuccess(new PageResult<>(caList, lastDoc));
                            })
                            .addOnFailureListener(err -> callback.onError(err.getMessage()));
                });
    }

    public void createConversation(ConversationModel conversation, final DataCallback<Void> callback) {
        firestore.collection("conversations").document(conversation.getConversationId()).set(conversation)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Chat ---

    public String getChatId(String uid1, String uid2) {
        if (uid1.compareTo(uid2) < 0) {
            return uid1 + "_" + uid2;
        } else {
            return uid2 + "_" + uid1;
        }
    }

    public void sendMessage(MessageModel message, final DataCallback<Void> callback) {
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

    public void updateMessage(MessageModel message, final DataCallback<Void> callback) {
        if (message.getId() == null) {
            callback.onError("Message ID is null");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("message", message.getMessage());
        if (message.getProposalStatus() != null) updates.put("proposalStatus", message.getProposalStatus());
        if (message.getProposalAmount() != null) updates.put("proposalAmount", message.getProposalAmount());
        if (message.getProposalDescription() != null) updates.put("proposalDescription", message.getProposalDescription());

        firestore.collection("conversations").document(message.getChatId())
                .collection("messages").document(message.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
    
    private void updateConversation(MessageModel message) {
        String chatId = message.getChatId();
        List<String> participants = Arrays.asList(message.getSenderId(), message.getReceiverId());
        
        Map<String, Object> updates = new java.util.HashMap<>();
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
    
    public void getConversations(String userId, final DataCallback<List<ConversationModel>> callback) {
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
                            // Filter out requests/refused conversations from the main chat list
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

                    // Fetch user details
                    populateUserDetails(userId, conversations, callback);
                });
    }

    public void getRequests(String userId, final DataCallback<List<ConversationModel>> callback) {
        firestore.collection("conversations")
                .whereArrayContains("participantIds", userId)
                //.whereEqualTo("workflowState", ConversationModel.STATE_REQUESTED) // Avoid composite index requirement
                //.orderBy("lastMessageTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING) 
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e.getMessage());
                        return;
                    }

                    List<ConversationModel> conversations = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ConversationModel conv = doc.toObject(ConversationModel.class);
                            if (conv != null && ConversationModel.STATE_REQUESTED.equals(conv.getWorkflowState())) {
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

    private void populateUserDetails(String userId, List<ConversationModel> conversations, final DataCallback<List<ConversationModel>> callback) {
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
                     UserModel user = userDoc.toObject(UserModel.class);
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
    
    public void sendRequest(String senderId, String receiverId, String initialMessageText, final DataCallback<Void> callback) {
        String chatId = getChatId(senderId, receiverId);
        
        firestore.collection("conversations").document(chatId).get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    ConversationModel existing = snapshot.toObject(ConversationModel.class);
                    // If refused or null state, reset to REQUESTED
                    if (existing != null && (ConversationModel.STATE_REFUSED.equals(existing.getWorkflowState()) || existing.getWorkflowState() == null)) {
                         // Reset state, but DON'T add message to collection yet
                         Map<String, Object> updates = new HashMap<>();
                         updates.put("workflowState", ConversationModel.STATE_REQUESTED);
                         updates.put("lastMessage", initialMessageText);
                         updates.put("lastMessageTimestamp", System.currentTimeMillis());
                         
                         firestore.collection("conversations").document(chatId).update(updates)
                             .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                             .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    } else {
                         // Already exists and not refused.
                         // If it is REQUESTED, update the last message (query) but don't add to collection
                         if (ConversationModel.STATE_REQUESTED.equals(existing.getWorkflowState())) {
                             Map<String, Object> updates = new HashMap<>();
                             updates.put("lastMessage", initialMessageText);
                             updates.put("lastMessageTimestamp", System.currentTimeMillis());
                             
                             firestore.collection("conversations").document(chatId).update(updates)
                                 .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                                 .addOnFailureListener(e -> callback.onError(e.getMessage()));
                         } else {
                             // Normal message if somehow allowed
                             sendMessage(new MessageModel(senderId, receiverId, chatId, initialMessageText, System.currentTimeMillis(), "TEXT"), callback);
                         }
                    }
                } else {
                    // New Conversation
                    ConversationModel newConv = new ConversationModel();
                    newConv.setConversationId(chatId);
                    newConv.setParticipantIds(Arrays.asList(senderId, receiverId));
                    newConv.setWorkflowState(ConversationModel.STATE_REQUESTED);
                    newConv.setLastMessage(initialMessageText);
                    newConv.setLastMessageTimestamp(System.currentTimeMillis());
                    
                    // Only set Conversation document, do NOT add to messages collection
                    firestore.collection("conversations").document(chatId).set(newConv)
                        .addOnSuccessListener(aVoid -> {
                            callback.onSuccess(null);
                        })
                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
                }
            })
            .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public ListenerRegistration listenForMessages(String chatId, final DataCallback<List<MessageModel>> callback) {
        return listenForRecentMessages(chatId, 50, callback);
    }

    public ListenerRegistration listenForRecentMessages(String chatId, int limit, final DataCallback<List<MessageModel>> callback) {
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

    public void getMessagesPage(String chatId, int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<MessageModel>> callback) {
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
                    callback.onSuccess(new PageResult<>(messages, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public ListenerRegistration listenToConversation(String chatId, final DataCallback<ConversationModel> callback) {
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

    public void updateConversationState(String chatId, String state, final DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("conversations").document(chatId);
        updateFieldWithRetry(ref, "workflowState", state, 2, callback);
    }

    public void updateVideoCallPermission(String chatId, boolean allowed, final DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("conversations").document(chatId);
        updateFieldWithRetry(ref, "videoCallAllowed", allowed, 2, callback);
    }

    public void acceptRequest(ConversationModel request, final DataCallback<Void> callback) {
        if (firebaseAuth.getCurrentUser() == null) {
            callback.onError("User not authenticated");
            return;
        }

        String currentUserId = firebaseAuth.getCurrentUser().getUid();
        String chatId = request.getConversationId();
        String initialMessageText = request.getLastMessage();
        
        // Robust sender identification: The sender of the request is the OTHER participant (not the current CA)
        String msgSenderId = null;
        if (request.getParticipantIds() != null) {
            for (String pid : request.getParticipantIds()) {
                if (!pid.equals(currentUserId)) {
                    msgSenderId = pid;
                    break;
                }
            }
        }
        
        // Fallback if logic fails (e.g. testing with self)
        if (msgSenderId == null) {
             if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
                 msgSenderId = request.getParticipantIds().get(0);
             } else {
                 callback.onError("No participants found");
                 return;
             }
        }
        
        String msgReceiverId = currentUserId;
        
        final String finalMsgSenderId = msgSenderId;
        MessageModel msg = new MessageModel(finalMsgSenderId, msgReceiverId, chatId, initialMessageText, System.currentTimeMillis(), "TEXT");
        
        // 1. Update State to DISCUSSION
        // 2. Add Message to subcollection
        
        firestore.collection("conversations").document(chatId)
                .update("workflowState", ConversationModel.STATE_DISCUSSION)
                .addOnSuccessListener(aVoid -> {
                    incrementClientCount(currentUserId, finalMsgSenderId); // Increment client count
                    firestore.collection("conversations").document(chatId).collection("messages").add(msg)
                            .addOnSuccessListener(ref -> callback.onSuccess(null))
                            .addOnFailureListener(e -> callback.onError("State updated but message failed: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateProposalStatus(String chatId, String messageId, String status, final DataCallback<Void> callback) {
        firestore.collection("conversations").document(chatId).collection("messages").document(messageId)
                .update("proposalStatus", status)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Stats ---

    public void incrementClientCount(String caId, String clientId) {
        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentReference clientRef = firestore.collection("users").document(caId).collection("clients").document(clientId);
            DocumentSnapshot clientSnapshot = transaction.get(clientRef);

            if (!clientSnapshot.exists()) {
                // Not a client yet, so add them and increment count
                Map<String, Object> data = new HashMap<>();
                data.put("timestamp", System.currentTimeMillis());
                transaction.set(clientRef, data);

                com.google.firebase.firestore.DocumentReference caRef = firestore.collection("users").document(caId);
                transaction.update(caRef, "clientCount", com.google.firebase.firestore.FieldValue.increment(1));
            }
            return null;
        }).addOnFailureListener(e -> {
            // Log error silently
        });
    }
    
    public void getRevenueStats(String userId, final DataCallback<Double> callback) {
        firestore.collection("transactions")
                .whereEqualTo("caId", userId)
                .whereEqualTo("status", "SUCCESS")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double total = 0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Double amount = doc.getDouble("amount");
                        if (amount != null) {
                            total += amount;
                        }
                    }
                    callback.onSuccess(total);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Transactions ---

    public void createTransaction(TransactionModel transaction, final DataCallback<Void> callback) {
        firestore.collection("transactions").document(transaction.getTransactionId())
                .set(transaction)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getTransactions(String userId, final DataCallback<List<TransactionModel>> callback) {
        getTransactionsPage(userId, 50, null, new DataCallback<PageResult<TransactionModel>>() {
            @Override
            public void onSuccess(PageResult<TransactionModel> data) {
                callback.onSuccess(data.getItems());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getTransactionsPage(String userId, int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<TransactionModel>> callback) {
        Query query = firestore.collection("transactions")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TransactionModel> transactions = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        transactions.add(doc.toObject(TransactionModel.class));
                    }
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new PageResult<>(transactions, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateWalletBalance(String userId, double amount, final DataCallback<Double> callback) {
        firestore.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel user = documentSnapshot.toObject(UserModel.class);
                        if (user != null) {
                            double newBalance = user.getWalletBalance() + amount;
                            firestore.collection("users").document(userId)
                                    .update("walletBalance", newBalance)
                                    .addOnSuccessListener(aVoid -> callback.onSuccess(newBalance))
                                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
                        } else {
                            callback.onError("User not found");
                        }
                    } else {
                        callback.onError("User document does not exist");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getWalletBalance(String userId, final DataCallback<Double> callback) {
        firestore.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel user = documentSnapshot.toObject(UserModel.class);
                        if (user != null) {
                            callback.onSuccess(user.getWalletBalance());
                        } else {
                            callback.onSuccess(0.0);
                        }
                    } else {
                        callback.onError("User not found");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getUserTransactions(String userId, final DataCallback<List<TransactionModel>> callback) {
        // Implementation might be duplicate of getTransactions above, but keeping signature
        getTransactions(userId, callback);
    }

    // --- Bookings ---

    public void saveBooking(com.example.taxconnect.model.BookingModel booking, final DataCallback<Void> callback) {
        String id = booking.getId() != null ? booking.getId() : firestore.collection("bookings").document().getId();
        booking.setId(id);
        firestore.collection("bookings").document(id)
                .set(booking)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getBookingsForUser(String userId, final DataCallback<List<com.example.taxconnect.model.BookingModel>> callback) {
        getBookingsForUserPage(userId, 50, null, new DataCallback<PageResult<com.example.taxconnect.model.BookingModel>>() {
            @Override
            public void onSuccess(PageResult<com.example.taxconnect.model.BookingModel> data) {
                callback.onSuccess(data.getItems());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getBookingsForCA(String caId, final DataCallback<List<com.example.taxconnect.model.BookingModel>> callback) {
        getBookingsForCaPage(caId, 50, null, new DataCallback<PageResult<com.example.taxconnect.model.BookingModel>>() {
            @Override
            public void onSuccess(PageResult<com.example.taxconnect.model.BookingModel> data) {
                callback.onSuccess(data.getItems());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getBookingsForUserPage(String userId, int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<com.example.taxconnect.model.BookingModel>> callback) {
        Query query = firestore.collection("bookings")
                .whereEqualTo("userId", userId)
                .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.BookingModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        list.add(doc.toObject(com.example.taxconnect.model.BookingModel.class));
                    }
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new PageResult<>(list, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getBookingsForCaPage(String caId, int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<com.example.taxconnect.model.BookingModel>> callback) {
        Query query = firestore.collection("bookings")
                .whereEqualTo("caId", caId)
                .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.BookingModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        list.add(doc.toObject(com.example.taxconnect.model.BookingModel.class));
                    }
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new PageResult<>(list, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateBookingStatus(String bookingId, String status, final DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("bookings").document(bookingId);
        updateFieldWithRetry(ref, "status", status, 2, callback);
    }

    public void blockUser(String currentUserId, String targetUserId, final DataCallback<Void> callback) {
        firestore.collection("users").document(currentUserId)
                .update("blockedUsers", com.google.firebase.firestore.FieldValue.arrayUnion(targetUserId))
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void submitReview(com.example.taxconnect.model.ReviewModel review, final DataCallback<Void> callback) {
        firestore.collection("reviews").document(review.getReviewId()).set(review)
                .addOnSuccessListener(aVoid -> {
                    updateCaRating(review.getCaId(), review.getRating(), callback);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private void updateCaRating(String caId, float newRating, final DataCallback<Void> callback) {
        firestore.collection("users").document(caId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserModel user = doc.toObject(UserModel.class);
                        if (user != null) {
                            double currentRating = user.getRating();
                            int count = user.getRatingCount();
                            
                            double total = currentRating * count;
                            total += newRating;
                            count++;
                            double avg = total / count;
                            
                            user.setRating(avg);
                            user.setRatingCount(count);
                            
                            updateUser(user, callback);
                        } else {
                            callback.onSuccess(null);
                        }
                    } else {
                        callback.onError("User not found");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getCaTransactions(String caId, final DataCallback<List<TransactionModel>> callback) {
        firestore.collection("transactions")
                .whereEqualTo("caId", caId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TransactionModel> transactions = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        transactions.add(doc.toObject(TransactionModel.class));
                    }
                    callback.onSuccess(transactions);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }


    // --- Community Forum ---

    public void createPost(com.example.taxconnect.model.PostModel post, final DataCallback<Void> callback) {
        firestore.collection("posts").document(post.getId()).set(post)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void toggleLike(String postId, String userId, final DataCallback<Boolean> callback) {
        com.google.firebase.firestore.DocumentReference postRef = firestore.collection("posts").document(postId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(postRef);
            if (!snapshot.exists()) return null;

            com.example.taxconnect.model.PostModel post = snapshot.toObject(com.example.taxconnect.model.PostModel.class);
            if (post == null) return null;

            List<String> likedBy = post.getLikedBy();
            if (likedBy == null) likedBy = new ArrayList<>();

            boolean isLiked;
            if (likedBy.contains(userId)) {
                likedBy.remove(userId);
                isLiked = false;
            } else {
                likedBy.add(userId);
                isLiked = true;
            }

            transaction.update(postRef, "likedBy", likedBy, "likeCount", likedBy.size());
            return isLiked;
        }).addOnSuccessListener(isLiked -> {
            if (isLiked != null) {
                callback.onSuccess(isLiked);
            } else {
                callback.onError("Post not found");
            }
        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getPosts(final DataCallback<List<com.example.taxconnect.model.PostModel>> callback) {
        String cacheKey = "posts_page0";
        long ttl = 45_000L;
        if (isCacheValid(cacheKey, ttl)) {
            respondFromCache(cacheKey, new DataCallback<List<com.example.taxconnect.model.PostModel>>() {
                @Override public void onSuccess(List<com.example.taxconnect.model.PostModel> data) { callback.onSuccess(data); }
                @Override public void onError(String error) { }
            });
            return;
        }
        getPostsPage(25, null, new DataCallback<PageResult<com.example.taxconnect.model.PostModel>>() {
            @Override
            public void onSuccess(PageResult<com.example.taxconnect.model.PostModel> data) {
                cache.put(cacheKey, new CacheEntry(data.getItems(), System.currentTimeMillis()));
                callback.onSuccess(data.getItems());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getPostsPage(int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<com.example.taxconnect.model.PostModel>> callback) {
        Query query = firestore.collection("posts")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.PostModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        list.add(doc.toObject(com.example.taxconnect.model.PostModel.class));
                    }
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new PageResult<>(list, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Comments ---

    public void getComments(String postId, final DataCallback<List<com.example.taxconnect.model.CommentModel>> callback) {
        getCommentsPage(postId, 50, null, new DataCallback<PageResult<com.example.taxconnect.model.CommentModel>>() {
            @Override
            public void onSuccess(PageResult<com.example.taxconnect.model.CommentModel> data) {
                callback.onSuccess(data.getItems());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCommentsPage(String postId, int limit, DocumentSnapshot lastSnapshot, final DataCallback<PageResult<com.example.taxconnect.model.CommentModel>> callback) {
        Query query = firestore.collection("posts").document(postId).collection("comments")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit);
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot);
        }
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.CommentModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        list.add(doc.toObject(com.example.taxconnect.model.CommentModel.class));
                    }
                    java.util.Collections.reverse(list);
                    DocumentSnapshot lastDoc = queryDocumentSnapshots.getDocuments().isEmpty()
                            ? null
                            : queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
                    callback.onSuccess(new PageResult<>(list, lastDoc));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void addComment(String postId, com.example.taxconnect.model.CommentModel comment, final DataCallback<Void> callback) {
        firestore.collection("posts").document(postId).collection("comments").document(comment.getId())
                .set(comment)
                .addOnSuccessListener(aVoid -> {
                    // Update comment count
                    firestore.collection("posts").document(postId)
                            .update("commentCount", com.google.firebase.firestore.FieldValue.increment(1));
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // --- Milestones ---

    public void getMilestones(String bookingId, final DataCallback<List<com.example.taxconnect.model.MilestoneModel>> callback) {
        firestore.collection("bookings").document(bookingId).collection("milestones")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<com.example.taxconnect.model.MilestoneModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        list.add(doc.toObject(com.example.taxconnect.model.MilestoneModel.class));
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateMilestoneStatus(String bookingId, String milestoneId, String status, final DataCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("bookings").document(bookingId)
                .collection("milestones").document(milestoneId);
        updateFieldWithRetry(ref, "status", status, 2, callback);
    }

    public void addMilestone(String bookingId, com.example.taxconnect.model.MilestoneModel milestone, final DataCallback<Void> callback) {
        firestore.collection("bookings").document(bookingId).collection("milestones").document(milestone.getId())
                .set(milestone)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
