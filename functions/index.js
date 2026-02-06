const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();

async function sendAndLogNotification(payload, logData) {
  const result = await admin.messaging().send(payload);
  try {
    await db.collection("notificationLogs").add({
      ...logData,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  } catch (e) {
    console.error("Error logging notification:", e);
  }
  return result;
}

/**
 * Triggered when a new message is added to a conversation.
 * Sends a notification to the receiver.
 */
exports.sendChatNotification = functions.firestore
  .document("conversations/{chatId}/messages/{messageId}")
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const senderId = message.senderId;
    const receiverId = message.receiverId;
    const messageText = message.message;
    const type = message.type; // TEXT, IMAGE, PROPOSAL, etc.

    // Don't send notification to self (shouldn't happen with correct logic but good safeguard)
    if (!receiverId) return null;

    try {
      // 1. Get sender's name
      const senderDoc = await admin.firestore().collection("users").document(senderId).get();
      const senderName = senderDoc.exists ? senderDoc.data().name : "Someone";
      const senderAvatar = senderDoc.exists ? senderDoc.data().profileImageUrl : null;

      // 2. Get receiver's FCM token
      const receiverDoc = await admin.firestore().collection("users").document(receiverId).get();
      if (!receiverDoc.exists) return null;

      const receiverData = receiverDoc.data();
      const fcmToken = receiverData.fcmToken;

      if (!fcmToken) {
        console.log(`No FCM token for user ${receiverId}`);
        return null;
      }

      let body = messageText;
      let notificationType = "message";
      let title = senderName;

      if (type === "IMAGE") body = "📷 Sent an image";
      if (type === "PROPOSAL") body = "📄 Sent a proposal";
      if (type === "CALL") {
          body = "Incoming Video Call...";
          title = senderName;
          notificationType = "call";
      }

      const payload = {
        token: fcmToken,
        data: {
          type: notificationType,
          senderId: senderId,
          senderName: senderName,
          callerName: senderName,
          callerAvatar: senderAvatar || "",
          chatId: context.params.chatId,
          title: title,
          body: body
        },
        android: {
          priority: "high",
          notification: {
            channelId: "tax_connect_notifications"
          }
        }
      };

      if (notificationType === "call") {
        payload.data.channelName = context.params.chatId;
        payload.data.roomUuid = context.params.chatId;
      } else {
        payload.notification = {
          title: title,
          body: body
        };
      }

      return sendAndLogNotification(payload, {
        category: "chat",
        notificationType,
        chatId: context.params.chatId,
        messageId: context.params.messageId,
        senderId,
        receiverId,
      });
    } catch (error) {
      console.error("Error sending chat notification:", error);
      return null;
    }
  });

/**
 * Triggered when a conversation state changes to REQUESTED.
 * Sends a notification to the CA.
 */
exports.sendRequestNotification = functions.firestore
  .document("conversations/{chatId}")
  .onWrite(async (change, context) => {
    const newData = change.after.exists ? change.after.data() : null;
    const oldData = change.before.exists ? change.before.data() : null;

    // Check if workflowState changed to REQUESTED
    if (
      newData &&
      newData.workflowState === "Requested" &&
      (!oldData || oldData.workflowState !== "Requested")
    ) {
      // Identify the sender and receiver (CA)
      // In a request, the one who initiated the "lastMessage" is likely the sender (User)
      // The other participant is the CA.
      // However, we don't strictly know who is who from participantIds list order.
      // But we know the sender of the last message (User) is the one requesting.
      
      // We can use the logic: The one who is NOT the author of the last update is the target.
      // But we don't have "author" field easily.
      // Let's assume the user is the one initiating.
      
      // A better way: The conversation document doesn't store "senderId" of the request explicitly 
      // other than implied by "lastMessage".
      // But we can infer from participants. 
      // We need to fetch both users to find who is the CA.
      
      const participants = newData.participantIds;
      if (!participants || participants.length < 2) return null;

      try {
        const userDocs = await Promise.all(
            participants.map(uid => admin.firestore().collection("users").document(uid).get())
        );
        
        let senderName = "A User";
        let caToken = null;
        let caId = null;

        // Find which user is CA and which is Customer
        // Logic: The one receiving the request is the CA.
        // But what if both are CAs?
        // Let's look at who SENT the request. 
        // We can't know for sure without an explicit "requesterId" field in ConversationModel.
        // BUT, usually the User requests the CA.
        
        // Let's iterate and find the one with role="CA".
        // If both are CAs, this might be ambiguous, but usually User -> CA.
        
        for (const doc of userDocs) {
            if (!doc.exists) continue;
            const data = doc.data();
            if (data.role === "CA") {
                caToken = data.fcmToken;
                caId = doc.id;
            } else {
                senderName = data.name;
            }
        }
        
        if (caToken) {
             const payload = {
                token: caToken,
                notification: {
                  title: "New Assistance Request",
                  body: `${senderName} has requested your assistance.`,
                },
                data: {
                  type: "request",
                  requestId: context.params.chatId,
                },
                android: {
                    priority: "high",
                    notification: {
                        channelId: "tax_connect_notifications"
                    }
                }
              };

              return sendAndLogNotification(payload, {
                category: "request",
                notificationType: "request",
                chatId: context.params.chatId,
                caId,
                senderName,
              });
        }
        
      } catch (error) {
          console.error("Error sending request notification:", error);
      }
    }
    return null;
  });
