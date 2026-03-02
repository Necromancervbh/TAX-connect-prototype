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
      const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
      const senderName = senderDoc.exists ? senderDoc.data().name : "Someone";
      const senderAvatar = senderDoc.exists ? senderDoc.data().profileImageUrl : null;

      // 2. Get receiver's FCM token and preferences
      const receiverDoc = await admin.firestore().collection("users").doc(receiverId).get();
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
      let channelId = "channel_messages";

      if (type === "IMAGE") body = "📷 Sent an image";
      if (type === "PROPOSAL") body = "📄 Sent a proposal";
      if (type === "CALL") {
        body = "Incoming Video Call...";
        title = senderName;
        notificationType = "call";
        channelId = "channel_calls";
      }

      // Check preferences
      if (notificationType === "message" && receiverData.notifyMessages === false) {
        console.log(`User ${receiverId} has disabled message notifications`);
        return null;
      }
      if (notificationType === "call" && receiverData.notifyCalls === false) {
        console.log(`User ${receiverId} has disabled call notifications`);
        return null;
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
          priority: "high"
        }
      };

      if (notificationType === "call") {
        payload.data.channelName = context.params.chatId;
        payload.data.roomUuid = context.params.chatId;
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
      const participants = newData.participantIds;
      if (!participants || participants.length < 2) return null;

      try {
        const userDocs = await Promise.all(
          participants.map(uid => admin.firestore().collection("users").doc(uid).get())
        );

        let senderName = "A User";
        let senderId = null;
        let caToken = null;
        let caId = null;
        let caData = null;

        for (const doc of userDocs) {
          if (!doc.exists) continue;
          const data = doc.data();
          if (data.role === "CA") {
            caToken = data.fcmToken;
            caId = doc.id;
            caData = data;
          } else {
            senderName = data.name;
            senderId = doc.id;
          }
        }

        if (caToken) {
          // Check CA preferences
          if (caData && caData.notifyRequests === false) {
            console.log(`CA ${caId} has disabled request notifications`);
            return null;
          }

          const payload = {
            token: caToken,
            data: {
              type: "request",
              requestId: context.params.chatId,
              title: "New Assistance Request",
              body: `${senderName} has requested your assistance.`,
              senderId: senderId || ""
            },
            android: {
              priority: "high"
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

/**
 * Triggered when a new booking is created or updated.
 * Sends a notification to the relevant user.
 */
exports.sendBookingNotification = functions.firestore
  .document("bookings/{bookingId}")
  .onWrite(async (change, context) => {
    const newData = change.after.exists ? change.after.data() : null;
    const oldData = change.before.exists ? change.before.data() : null;

    if (!newData) return null; // Deletion

    const bookingId = context.params.bookingId;
    const caId = newData.caId;
    const userId = newData.userId;
    const status = newData.status;

    // 1. If newly created, notify the CA
    if (!oldData) {
      try {
        const [caDoc, userDoc] = await Promise.all([
          admin.firestore().collection("users").doc(caId).get(),
          admin.firestore().collection("users").doc(userId).get()
        ]);

        if (caDoc.exists && caDoc.data().fcmToken) {
          const caData = caDoc.data();
          const userName = userDoc.exists ? userDoc.data().name : "A user";

          if (caData.notifyBookings === false) return null;

          const payload = {
            token: caData.fcmToken,
            data: {
              type: "booking",
              bookingId: bookingId,
              isRequest: "true",
              title: "New Booking Request",
              body: `${userName} has requested a booking.`
            },
            android: {
              priority: "high"
            }
          };

          return sendAndLogNotification(payload, {
            category: "booking",
            notificationType: "new_booking",
            bookingId,
            caId,
            userId
          });
        }
      } catch (error) {
        console.error("Error sending new booking notification:", error);
      }
    }

    // 2. If status changed, notify the User (e.g. Accepted, Rejected)
    if (oldData && oldData.status !== status) {
      try {
        const [userDoc, caDoc] = await Promise.all([
          admin.firestore().collection("users").doc(userId).get(),
          admin.firestore().collection("users").doc(caId).get()
        ]);

        if (userDoc.exists && userDoc.data().fcmToken) {
          const userData = userDoc.data();
          const caName = caDoc.exists ? caDoc.data().name : "Chartered Accountant";

          if (userData.notifyBookings === false) return null;

          let title = "Booking Update";
          let body = `Your booking status has been updated to ${status}.`;

          if (status === "ACCEPTED") {
            title = "Booking Accepted";
            body = `${caName} has accepted your booking request.`;
          } else if (status === "REJECTED") {
            title = "Booking Declined";
            body = `${caName} has declined your booking request.`;
          }

          const payload = {
            token: userData.fcmToken,
            data: {
              type: "booking",
              bookingId: bookingId,
              status: status,
              title: title,
              body: body
            },
            android: {
              priority: "high"
            }
          };

          return sendAndLogNotification(payload, {
            category: "booking",
            notificationType: "status_update",
            bookingId,
            caId,
            userId,
            status
          });
        }
      } catch (error) {
        console.error("Error sending booking status update notification:", error);
      }
    }

    return null;
  });
