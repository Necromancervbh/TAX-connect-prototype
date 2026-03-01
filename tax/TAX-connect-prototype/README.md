# TaxConnect Prototype

**TaxConnect** is a comprehensive professional service marketplace application designed to bridge the gap between **Taxpayers (Customers)** and **Chartered Accountants (CAs)**. It serves as a secure platform for finding experts, scheduling consultations, managing tax documents, and conducting video consultations.

> **Note:** The Android application source code is located in the `tax/` directory of this repository.

---

## 🚀 Key Features

### 👥 Role-Based Experience
*   **Customer Dashboard:** Search and filter CAs by name or city, view profiles, and initiate requests.
*   **CA Dashboard:** Manage client requests, track revenue/active clients, and toggle availability.

### 🔒 Secure Communication
*   **Real-Time Chat:** Integrated messaging with text, image, and document sharing.
*   **Privacy First:** `FLAG_SECURE` implementation prevents screenshots and screen recordings in sensitive chats.
*   **Video Consultations:** High-quality in-app video calls powered by **Agora SDK**.

### 📅 Appointment Management
*   **Dynamic Booking:** Seamless booking flow via chat or profile with a dynamic calendar.
*   **Status Tracking:** Track appointments (Pending, Accepted, Refused, Expired) via `MyBookingsActivity`.

### 📂 Tax Locker
*   **Document Management:** Securely upload and organize personal tax documents.
*   **Smart Categorization:** Organize files into folders (Returns, PAN, Identity, etc.) using horizontal chips.

### 💬 Community Forum
*   **Public Q&A:** Users can post tax-related queries.
*   **Expert Verification:** Comments from CAs are highlighted with a "CA" role badge for credibility.

### 🏆 Trust & Verification
*   **Verified Badges:** Manual verification system for CAs (`isVerified` status).
*   **Secure Certificates:** CAs can showcase credentials that are view-only and protected from downloading.

### 📈 Service Milestones
*   **Escrow-Style Tracking:** Transparently track service deliverables via `MilestonesActivity`.
*   **Progress Monitoring:** Visual progress bars and status updates (Pending -> In Progress -> Completed).

---

## 🛠️ Tech Stack

*   **Language:** Kotlin/Java (Native Android)
*   **UI/UX:** Material Design 3, Custom Drawables, Bottom Sheets
*   **Backend:** Firebase Firestore (NoSQL Database), Firebase Authentication
*   **Storage:** Firebase Storage, Cloudinary (for optimized media)
*   **Notifications:** Firebase Cloud Messaging (FCM) HTTP v1
*   **Video Calls:** Agora SDK

---

## ⚙️ Setup & Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/Necromancervbh/TAX-connect-prototype.git
    ```
2.  **Open the Project:**
    *   Open Android Studio.
    *   Go to **File > Open** and select the `tax/` directory.
3.  **Firebase Configuration:**
    *   Place a valid `google-services.json` file in the `tax/app/` directory (if not already present).
4.  **Build the Project:**
    *   Sync Gradle files and build the project.
    *   Run on an Emulator or Physical Device.
