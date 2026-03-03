---
description: Comprehensive overview of the TaxConnect app — what it does, how it works, its architecture, features, and data flows
---

# TaxConnect — App Overview

## What Is This App?

**TaxConnect** is an Android app that connects **clients** (general users) with **Chartered Accountants (CAs)** for tax and financial services. Think of it as an "Uber for accounting" — clients browse, book, chat with, and pay CAs through the app.

## User Roles

There are **two user roles**, determined by `UserModel.role`:

| Role | Landing Screen | Can Do |
|------|---------------|--------|
| **Client** (default) | `HomeActivity` | Browse CAs, book services, chat, pay, upload documents, join community |
| **CA** (Chartered Accountant) | `CADashboardActivity` | Manage clients, accept/reject bookings, set services & pricing, track revenue, withdraw earnings |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| DI | Hilt (partial — some manual singletons remain) |
| Auth | Firebase Authentication |
| Database | Cloud Firestore |
| File Storage | Cloudinary (images, videos, documents) |
| Payments | Razorpay (external) + In-app Wallet |
| Video Calls | Agora SDK |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Analytics | Firebase Analytics + custom Firestore logging |
| Performance | Firebase Performance Monitoring |
| Crash Reporting | Firebase Crashlytics |
| Background Work | WorkManager (offline sync, reply actions) |
| Logging | Timber |

## Architecture

```
com.example.taxconnect/
├── MyApplication.kt          ← App init (Firebase, Cloudinary, Razorpay, Timber, theme)
├── core/                     ← Shared infrastructure
│   ├── base/                 ← BaseActivity, BaseViewModel, BaseRepository, BaseUseCase
│   ├── common/               ← Constants, Resource sealed class
│   ├── error/                ← ErrorHandler (translates exceptions → user messages)
│   ├── navigation/           ← NavigationManager (centralized intent routing)
│   ├── network/              ← NetworkUtils (connectivity checks)
│   ├── preferences/          ← PreferencesManager (app settings)
│   ├── ui/                   ← Material3Button, Material3MotionHelper, ThemeHelper
│   ├── utils/                ← FileUtils, SecurityUtils, PaymentUtils, ListUtils, etc.
│   └── validation/           ← ValidationUtils (input validation rules)
├── data/                     ← Data layer
│   ├── models/               ← 15 Firestore data classes (UserModel, BookingModel, etc.)
│   ├── repositories/         ← 10 repository classes (DataRepository is the main one, 75KB)
│   └── services/             ← FCM service, Agora manager, Cloudinary, Workers
└── features/                 ← UI features (Activity + ViewModel + Adapter per feature)
    ├── auth/                 ← Login, Register, Splash + LoginUseCase
    ├── booking/              ← MyBookings, OrderHistory, Requests
    ├── ca/                   ← ExploreCAs, CADetail (browse & view CA profiles)
    ├── chat/                 ← Chat, MyChats + dialogs (payment, proposal)
    ├── community/            ← Community posts, comments
    ├── dashboard/            ← CA Dashboard (revenue, clients, bookings)
    ├── documents/            ← MyDocuments, SecureDocViewer, BalanceSheet
    ├── home/                 ← Home (client landing), MainActivity
    ├── milestones/           ← Achievement tracking
    ├── notification/         ← NotificationHistory, NotificationSettings
    ├── profile/              ← Profile editing, services management
    ├── videocall/            ← Agora video calls, video player
    └── wallet/               ← Wallet balance, deposits, withdrawals, transactions
```

## Key User Flows

### 1. Authentication Flow
```
SplashActivity → checks FirebaseAuth
  ├── Logged in? → NavigationManager.navigateToDashboard()
  │                  ├── CA role → CADashboardActivity
  │                  └── Client role → HomeActivity
  └── Not logged in? → LoginActivity → RegisterActivity (if new)
```

### 2. Client: Hiring a CA
```
HomeActivity (browse top/featured CAs)
  → ExploreCAsActivity (search, filter, sort all CAs)
    → CADetailActivity (view profile, ratings, services, intro video)
      → "Request Service" → creates ConversationModel with STATE_REQUESTED
        → ChatActivity (real-time messaging with the CA)
```

### 3. Chat & Service Workflow
The chat has a **workflow state machine** tracked by `ConversationModel.workflowState`:
```
STATE_REQUESTED → (CA accepts) → STATE_ACCEPTED
  → (Work in progress, messages, file sharing)
  → (CA sends payment request via ProposalDialogHelper)
  → STATE_PAYMENT_PENDING → (Client pays) → STATE_COMPLETED
  → (Client rates the CA via RatingModel)
```

### 4. Payment Flow
Two payment methods:
- **Razorpay** (external): Client pays via Razorpay gateway → `processExternalPayment()`
- **In-app Wallet**: Client deposits via Razorpay → wallet balance → pays CA from wallet → `payWithWallet()`

Wallet operations: `WalletViewModel.performDeposit()` / `performWithdrawal()`

### 5. CA Dashboard
`CADashboardViewModel.loadDashboardData()` fetches in parallel:
- User profile & online status
- Revenue stats (total, breakdown by period)
- Client stats (active, returning clients)
- Pending requests (prioritized by returning clients & recency)
- Upcoming bookings (accept/reject)
- Wallet balance

### 6. Video Calls
```
ChatActivity → "Video Call" button
  → VideoCallActivity (Agora SDK)
     - Creates channel, generates token
     - Supports PiP mode
     - CallActionReceiver handles incoming call notifications
```

### 7. Document Management
```
MyDocumentsActivity → browse folders/documents
  → SecureDocViewerActivity → renders PDFs securely (no screenshots, auto-delete temp files)
```

### 8. Push Notifications
`MyFirebaseMessagingService` handles:
- New message notifications (with quick reply via `ReplyWorker`)
- Incoming call alerts (with accept/reject actions via `CallActionReceiver`)
- Booking status updates
- Payment notifications

## Data Models (Firestore Collections)

| Model | Firestore Collection | Purpose |
|-------|---------------------|---------|
| `UserModel` | `users` | User profile, role, ratings, online status |
| `ConversationModel` | `conversations` | Chat threads with workflow state |
| `MessageModel` | `conversations/{id}/messages` | Individual messages (text, file, payment request) |
| `BookingModel` | `bookings` | Service bookings with time slots |
| `TransactionModel` | `transactions` | Wallet transactions (deposits, withdrawals, payments) |
| `RatingModel` | `ratings` | Client reviews of CAs |
| `ServiceModel` | `services` | CA's offered services & pricing |
| `PostModel` | `posts` | Community forum posts |
| `CommentModel` | `posts/{id}/comments` | Comments on posts |
| `DocumentModel` | `documents` | Uploaded documents metadata |
| `NotificationModel` | `notifications` | Push notification history |
| `RequestItem` | `requests` | Service requests from clients |
| `MilestoneModel` | `milestones` | User achievement milestones |
| `CertificateModel` | `certificates` | CA professional certificates |
| `FeedbackModel` | `feedback` | App feedback/bug reports |

## Repositories

| Repository | Lines | Scope |
|-----------|-------|-------|
| `DataRepository` | ~1744 | **God-class** — handles users, conversations, messages, transactions, bookings, etc. Uses manual singleton. |
| `ChatRepository` | ~300 | Real-time message streaming, send/receive, file uploads |
| `ConversationRepository` | ~400 | Conversation CRUD, workflow state transitions |
| `WalletRepository` | ~400 | Balance management, transactions, deposits/withdrawals |
| `DashboardRepository` | ~170 | CA dashboard aggregation queries |
| `ProfileRepository` | ~120 | User profile CRUD, media uploads |
| `HomeRepository` | ~70 | CA list fetching, unread counts |
| `UserRepository` | ~120 | User lookup, status updates |
| `NotificationRepository` | ~70 | Notification history |
| `AnalyticsRepository` | ~65 | Event logging to Firestore + Firebase Analytics |

## Services & Workers

| Service | Purpose |
|---------|---------|
| `MyFirebaseMessagingService` | FCM push notification handler (messages, calls, bookings) |
| `CallActionReceiver` | BroadcastReceiver for call accept/reject from notification |
| `AgoraManager` | Agora SDK initialization and token management |
| `CloudinaryHelper` | Image/video upload to Cloudinary CDN |
| `PaymentManager` | Razorpay payment session management |
| `FirestoreSyncWorker` | Offline data sync via WorkManager |
| `ReplyWorker` | Quick reply from notification shade |
| `RequestStatusWorker` | Background booking status updates |

## Key Constants & Configuration

- **API Keys**: Read from `BuildConfig` (set in `local.properties`, injected via `build.gradle.kts`)
- **Firebase Config**: `app/google-services.json`
- **SDK Targets**: compileSdk=35, minSdk=24, targetSdk=35
- **Package**: `com.example.taxconnect`
- **App Class**: `.MyApplication` (HiltAndroidApp)
