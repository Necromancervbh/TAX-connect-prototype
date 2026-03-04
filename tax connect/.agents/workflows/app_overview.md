---
description: Comprehensive overview of the TaxConnect app — what it does, how it works, its architecture, features, and data flows
---

# App Overview

## What is TaxConnect?
TaxConnect is a marketplace app connecting Indian taxpayers (clients) with Chartered Accountants (CAs). Clients can browse CA profiles, book appointments, chat, make payments, upload documents, and leave reviews. CAs manage their bookings, view their revenue dashboard, set availability, and communicate with clients through a structured workflow.

---

## User Roles
| Role | Entry Activity | Home Screen |
|---|---|---|
| **Client** | `LoginActivity` → `HomeActivity` | CA discovery feed, "My Professionals", booking history |
| **CA** | `LoginActivity` → `CADashboardActivity` | Booking list, revenue chart, online status toggle |

Role is stored in `UserModel.role` field in Firestore (`"CLIENT"` or `"CA"`).

---

## Core Features

### 1. CA Discovery (Client)
- **`HomeActivity`** — horizontal carousel of CAs with search, voice search, and filter
- **`ExploreCAsActivity`** — full-screen CA list with filter/sort
- **`CADetailActivity`** — CA profile, services, reviews, and "Request Assistance" button
- Tapping "Request Assistance" → `BookAppointmentActivity`

### 2. Booking System
- **`BookAppointmentActivity`** — 3-step stepper: service → date/time → confirm
- **`MyBookingsActivity`** — tabbed view (Pending / Upcoming / Completed / Declined) for both client and CA
- **`CADashboardActivity`** — CA sees pending bookings; can Accept (creates chat) or Reject
- Status flow: `PENDING → ACCEPTED → COMPLETED / REJECTED / CANCELLED`

### 3. Chat & Workflow
- **`ChatActivity`** — full-featured messaging screen
- Workflow states: `DISCUSSION → PAYMENT_PENDING → ADVANCE_PAYMENT → DOCS_PENDING → COMPLETED`
- CA actions: Create Proposal, Request Docs, Complete Job, Video Call
- Client actions: Book Appointment, Pay (via Razorpay), Leave Feedback, Book Again
- Sticky booking context banner in toolbar shows linked booking details

### 4. Payments (Razorpay)
- All payments go through `Razorpay Checkout`
- `onPaymentSuccess` → `ChatViewModel.processExternalPayment` → wallet credit + conversation state advance
- `onPaymentError` → sticky `cardPaymentIssue` card with Retry / Support
- Wallet balance shown in `WalletActivity` with transaction history

### 5. Documents
- **`MyDocumentsActivity`** — folder-based document storage
- **`UploadDocumentBottomSheet`** — picks image or PDF, uploads to Firebase Storage
- **`SecureDocViewerActivity`** — view uploaded files securely in-app

### 6. Video Calls
- **`VideoCallActivity`** — WebRTC-based video call
- Launched from chat toolbar (CA) or Customer Video Call button (client)
- Requires CAMERA + RECORD_AUDIO permissions

### 7. CA Rating
- Eligible clients (have a COMPLETED booking with the CA) can rate 1–5 stars
- Entry: CADetailActivity → "Write a Review", or auto-prompt in ChatActivity on COMPLETED state
- Ratings stored in `users/{caId}/ratings/{reviewerId}`
- CA aggregate rating updated atomically via Firestore transaction

### 8. Community
- **`CommunityActivity`** — social feed of tax tips and updates
- **`PostDetailActivity`** — comments and reactions
- Currently available to both roles

### 9. Notifications
- **`MyFirebaseMessagingService`** — handles FCM push notifications
- Shows sender name + avatar (or initials fallback)
- **`NotificationHistoryActivity`** — in-app notification list
- **`NotificationSettingsActivity`** — per-category toggle settings

---

## Architecture

```
UI Layer          ViewModel Layer       Repository Layer       Firebase
──────────        ───────────────       ────────────────       ────────
Activity/         ChatViewModel ──────→ ConversationRepo  ──→ Firestore
Fragment          HomeViewModel ──────→ DataRepository    ──→ Auth
                  CADashboardVM ──────→ BookingRepository ──→ Storage
                  ProfileVM     ──────→                   ──→ Realtime DB
                                                          ──→ FCM
```

- **Base classes**: `BaseActivity<VB>` — provides `binding`, `initViews()`, `setupListeners()`, `observeViewModel()`
- **DI**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`)
- **State**: Kotlin `StateFlow` + `Resource<T>` sealed class (`Loading / Success / Error`)
- **Image loading**: Glide
- **Logging**: Timber

---

## Key Firestore Collections
| Collection | Contents |
|---|---|
| `users` | `UserModel` — profile, role, rating, ratingCount, fcmToken |
| `users/{caId}/ratings` | `RatingModel` per reviewer |
| `bookings` | `BookingModel` — status, dates, chatId |
| `conversations` | `ConversationModel` — workflowState, lastMessage, participantIds |
| `conversations/{chatId}/messages` | `MessageModel` per message |
| `wallets` | Balance + transaction log |

---

## Navigation Entry Points
See [navigation_map.md](navigation_map.md) for the full entry point map.
See [booking_flow.md](booking_flow.md), [chat_flow.md](chat_flow.md), [ca_rating_flow.md](ca_rating_flow.md) for detailed workflow docs.
