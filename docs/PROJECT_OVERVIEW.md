# TaxConnect - Application Overview

## 1. App Concept
**TaxConnect** is a professional service marketplace application that connects **Taxpayers (Customers)** with **Chartered Accountants (CAs)**. It functions similarly to a consultation platform where users can find experts, chat, and conduct video consultations.

---

## 2. User Personas & Architecture
The app has two distinct user roles with separate interfaces:
*   **Customer:** Seeks tax advice, searches for CAs, and initiates requests.
*   **Chartered Accountant (CA):** Provides services, manages client requests, and tracks revenue.

### Tech Stack
*   **Frontend:** Native Android (Java, XML, Material Design 3).
*   **Backend:** Firebase (Firestore Database, Authentication, Storage).
*   **Communication:** Agora SDK (Video Calls), Firebase Cloud Messaging (Notifications).

---

## 3. Detailed User Workflows

### A. Authentication Flow (Common)
1.  **Splash Screen:** Checks if the user is already logged in.
2.  **Login/Register:**
    *   Users sign up with Email/Password.
    *   **Crucial Step:** User selects their role (**"I am a Client"** or **"I am a CA"**).
    *   Data is saved to Firestore `users` collection.

### B. Customer Journey
1.  **Dashboard (`CustomerDashboardActivity`):**
    *   **Search:** Floating search bar to find CAs by name or city.
    *   **Filter:** Filter CAs by price range (Min/Max).
    *   **List:** RecyclerView displaying registered CAs with their photos, experience, and rating.
    *   **Navigation:** Side drawer to access Profile, My Chats, Wallet.
2.  **Connection:**
    *   Customer clicks on a CA card -> Opens **CA Detail**.
    *   Clicks "Request assistance" -> Creates a conversation entry in Firestore.
3.  **Interaction:**
    *   **Chat:** Sends text, images, or documents.
    *   **Video Call:** Initiates a video consultation via Agora.

### C. CA Journey
1.  **Dashboard (`CADashboardActivity`):**
    *   **Status Toggle:** Switch "Online/Offline" availability.
    *   **Stats Board:** Cards showing **Total Revenue** and **Active Clients**.
    *   **Recent Activity:** List of recent interactions.
2.  **Request Management:**
    *   **Requests Screen:** views incoming connection requests.
    *   **Action:** Accepts or Rejects requests (updates Firestore status).
3.  **Service Delivery:**
    *   **Proposals:** Can send "Payment Proposals" inside the chat for specific services.
    *   **Consultation:** Receives video calls from clients.

---

## 4. Key Features & Modules

| Feature | Description | Code Reference |
| :--- | :--- | :--- |
| **Real-time Chat** | Text, Image, and Document sharing. Supports "Sent" and "Received" bubbles. | `ChatActivity.java` |
| **Video Calling** | High-quality 720p video calls with Picture-in-Picture local view. | `VideoCallActivity.java` |
| **Smart Notifications** | Push notifications for new messages and incoming calls using FCM HTTP v1. | `FcmService.java` |
| **Data Repository** | Centralized class handling all Firebase interactions to keep UI code clean. | `DataRepository.java` |
| **Modern UI** | Material Design 3 implementation with Cards, Gradients, and Bottom Sheets. | `item_ca.xml` |

## 5. Data Structure (Firestore)
*   **`users/`**: Stores profile info, role (CA/Customer), and online status.
*   **`conversations/`**: Stores chat metadata (participants, last message, unread count).
*   **`conversations/{chatId}/messages`**: Sub-collection containing actual message history.
