# TaxConnect

**TaxConnect** is a comprehensive professional service marketplace application designed to bridge the gap between **Taxpayers (Customers)** and **Chartered Accountants (CAs)**. It serves as a secure platform for finding experts, scheduling consultations, managing tax documents, and conducting video consultations.

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

*   **Language:** Java (Native Android)
*   **UI/UX:** Material Design 3, Custom Drawables, Bottom Sheets
*   **Backend:** Firebase Firestore (NoSQL Database), Firebase Authentication
*   **Storage:** Firebase Storage, Cloudinary (for optimized media)
*   **Notifications:** Firebase Cloud Messaging (FCM) HTTP v1
*   **Video Calls:** Agora SDK
*   **Media Playback:** ExoPlayer (Media3)

---

## 📂 Documentation & Workflows

For detailed information on user flows, architecture, and feature implementation, please refer to the internal documentation:

*   **[APP_WORKFLOWS.md](APP_WORKFLOWS.md):** Comprehensive guide on user navigation, activity transitions, and specific feature logic.
*   **[PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md):** High-level architectural overview and concept definitions.
*   **[DESIGN_SYSTEM_GUIDE.md](docs/DESIGN_SYSTEM_GUIDE.md):** Design tokens, components, and UI standards.
*   **[PERFORMANCE_ACCESSIBILITY_AUDIT.md](docs/PERFORMANCE_ACCESSIBILITY_AUDIT.md):** Performance and accessibility findings.
*   **[VIDEO_CALL_AUDIT_REPORT.md](docs/VIDEO_CALL_AUDIT_REPORT.md):** Video call quality and risk audit.
*   **[VIDEO_CALL_DEPLOYMENT_GUIDE.md](docs/VIDEO_CALL_DEPLOYMENT_GUIDE.md):** Deployment steps for call features.

---

## 💳 Payment Platform

*   **Webhook retries:** POST `/agreements/webhooks/retry?limit=25` with `x-user-id` and `x-role` headers to replay failed accounting webhooks stored in `notifications`.
*   **Authentication:** All payment-platform routes require `x-user-id` and `x-role` headers.

---

## ⚙️ Setup & Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/your-repo/taxconnect.git
    ```
2.  **Firebase Configuration:**
    *   Place your `google-services.json` file in the `app/` directory.
3.  **Build the Project:**
    *   Open the project in Android Studio.
    *   Sync Gradle files.
    *   Run on an Emulator or Physical Device.

---

## 📱 Project Structure

*   `com.example.taxconnect.activity`: Contains all Activity classes (UI Controllers).
*   `com.example.taxconnect.adapter`: RecyclerView adapters for Lists (CAs, Chats, Documents, etc.).
*   `com.example.taxconnect.model`: Data models reflecting Firestore documents.
*   `com.example.taxconnect.repository`: `DataRepository` class for centralized Firebase operations.
*   `com.example.taxconnect.utils`: Helper classes (Date formatting, Permissions, etc.).

---

**Built for the Future of Tax Consultation.**
