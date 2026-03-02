const express = require("express");
const cors = require("cors");
const admin = require("firebase-admin");

require("dotenv").config();

// Initialize Firebase Admin SDK
try {
    let serviceAccount;

    if (process.env.FIREBASE_SERVICE_ACCOUNT_BASE64) {
        const buff = Buffer.from(process.env.FIREBASE_SERVICE_ACCOUNT_BASE64, 'base64');
        const text = buff.toString('utf-8');
        serviceAccount = JSON.parse(text);
    } else if (process.env.FIREBASE_SERVICE_ACCOUNT_PATH) {
        serviceAccount = require(process.env.FIREBASE_SERVICE_ACCOUNT_PATH);
    } else {
        console.warn("WARNING: No FIREBASE_SERVICE_ACCOUNT provided. Firebase Admin not initialized.");
    }

    if (serviceAccount) {
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        console.log("Firebase Admin successfully initialized.");
    }
} catch (error) {
    console.error("Error initializing Firebase Admin:", error);
}

const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

// Helper function to send FCM
const sendFcmNotification = async (fcmToken, dataPayload) => {
    if (!admin.apps.length) {
        throw new Error("Firebase Admin not initialized.");
    }

    const message = {
        token: fcmToken,
        data: dataPayload
    };

    try {
        const response = await admin.messaging().send(message);
        console.log("Successfully sent message:", response);
        return response;
    } catch (error) {
        console.error("Error sending message:", error);
        throw error;
    }
};

app.get("/health", (req, res) => {
    res.status(200).send("Notification Server is healthy.");
});

// Endpoint for Chat and Call Notifications
app.post("/sendChatNotification", async (req, res) => {
    const {
        recipientId,
        senderId,
        senderName,
        senderImage,
        chatId,
        messageContent,
        messageType
    } = req.body;

    if (!recipientId || !senderId || !chatId) {
        return res.status(400).send({ error: "Missing required fields" });
    }

    try {
        // Get Recipient's FCM Token from Firestore
        const userDoc = await admin.firestore().collection("users").doc(recipientId).get();
        if (!userDoc.exists) {
            return res.status(404).send({ error: "Recipient not found" });
        }

        const fcmToken = userDoc.data().fcmToken;
        if (!fcmToken) {
            return res.status(404).send({ error: "Recipient does not have an FCM token" });
        }

        let finalSenderName = senderName;
        let finalSenderImage = senderImage;
        if ((!finalSenderName || !finalSenderImage) && senderId) {
            const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
            if (senderDoc.exists) {
                finalSenderName = finalSenderName || senderDoc.data().name;
                finalSenderImage = finalSenderImage || senderDoc.data().profileImageUrl;
            }
        }

        let title = "New Message";
        let body = "You have a new message";
        let type = "message";

        if (messageType === "CALL") {
            title = "Incoming Video Call";
            body = `${finalSenderName || "Someone"} is calling you...`;
            type = "call";
        } else if (messageType === "TEXT") {
            title = finalSenderName || "New Message";
            body = messageContent || "Sent a message";
        } else if (messageType === "IMAGE") {
            title = finalSenderName || "New Message";
            body = "Sent an image";
        } else if (messageType === "DOCUMENT") {
            title = finalSenderName || "New Message";
            body = "Sent a document";
        } else if (messageType === "PAYMENT_REQUEST") {
            title = "Payment Request";
            body = `${finalSenderName || "A user"} requested a payment.`;
        }

        const payload = {
            title: title,
            body: body,
            type: type,
            senderId: String(senderId),
            chatId: String(chatId)
        };

        if (finalSenderImage) {
            payload.senderImage = String(finalSenderImage);
        }

        if (type === "call") {
            payload.channelName = String(chatId);
            payload.roomUuid = String(chatId);
            payload.callerName = String(finalSenderName || "Unknown");
            if (finalSenderImage) {
                payload.callerAvatar = String(finalSenderImage);
            }
        }

        await sendFcmNotification(fcmToken, payload);
        return res.status(200).send({ success: true, message: "Chat notification sent" });

    } catch (error) {
        console.error("Error in /sendChatNotification:", error);
        return res.status(500).send({ error: "Internal Server Error" });
    }
});

app.post("/sendRequestNotification", async (req, res) => {
    // Implementation for request notifications if needed
    // You can add logic for Proposals here later!
    res.status(200).send({ success: true, message: "Not implemented yet" });
});

app.post("/sendBookingNotification", async (req, res) => {
    // Implementation for booking notifications if needed
    res.status(200).send({ success: true, message: "Not implemented yet" });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
