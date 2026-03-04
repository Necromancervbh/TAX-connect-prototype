---
description: Chat and messaging workflow — proposal, payment, milestones, and rating
---

# Chat Workflow

## Overview
After a booking is accepted, both parties chat through `ChatActivity`. The CA can send proposals, request payment, and mark work complete. The client pays and leaves a review.

## Workflow States
These map to `ConversationModel.STATE_*` constants:

| State | Meaning | Who acts next |
|---|---|---|
| `DISCUSSION` | Chat open, free discussion | Either party |
| `REQUESTED` | Legacy state (kept for backwards compat) | CA |
| `PAYMENT_PENDING` | CA sent a proposal, awaiting client acceptance | Client |
| `ADVANCE_PAYMENT` | Client accepted proposal, advance due | Client pays |
| `DOCS_PENDING` | Advance paid, CA is working on docs | CA |
| `COMPLETED` | Final payment done, job complete | Client rates |
| `REFUSED` | CA/client declined | — |

## Chat Screens

### Workflow Status Bar
The sticky status bar below the toolbar shows:
- `tvWorkflowStatus` — e.g. "Status: Advance Payment"
- `tvWorkflowNext` — the next action hint (e.g. "→ Client should pay advance")
- `layoutStepper` / `layoutStepperLabels` — 3-step visual progress indicator (shown only during payment/doc states)

### Suggestion Chips (Empty State)
When no messages exist yet, 3 quick-start buttons appear:
- 👋 Hello! I'm ready to get started
- 📎 I have some documents to share
- ❓ I have a few questions first

### Action Row (After Chat Begins)
Horizontal scroll of context-sensitive action buttons:
- **Book Appointment** (`btnBookAppointment`) — re-book from within chat
- **Book Again** (`btnRequestAgain`) — after completion, start fresh
- **Customer Video Call** (`btnCustomerVideoCall`)
- **Leave Feedback** (`btnLeaveFeedback`)
- **View History** (`btnViewHistory`)

## CA Toolbar Actions
- Send a **Proposal** (`btnCreateProposal`) — opens `ProposalDialogHelper`
- **Video Call** (`btnVideoCall`) — triggers `checkPermissionsAndStartCall()`
- **Attach** (`btnAttach`) — image/PDF file picking with optimistic UI

## Payment Flow Inside Chat
1. CA uses `ProposalDialogHelper.showCreateProposalDialog()` to propose a price.
2. Client taps **Accept** on the proposal message → `onAccept()` transitions to `STATE_PAYMENT_PENDING`.
3. Client taps **Pay Advance** → `initiatePayment()` launches Razorpay checkout.
4. On `onPaymentSuccess()`, wallet is credited and conversation state advances.

## Rating Flow
1. On `STATE_COMPLETED`, `showRateCaDialog()` opens automatically (once per session).
2. Rating is submitted via `ChatViewModel.submitRating()` / `DataRepository.addRating()`.
3. On successful submission, conversation resets to `STATE_DISCUSSION` for future work.

## Implementation Nuances & Troubleshooting
- **Chat Initialization**: Chat deep-links or intent startups often only know `currentUserId` and `otherUserId`. In `ChatViewModel.initializeChatByUsers()`, the system must query `ConversationRepository.findExistingChatId()` first to support legacy random document IDs, gracefully falling back to creating a deterministic (`uid1_uid2`) ID.
- **Empty State Initialization Block**: In brand new chats, the chat document isn't created on Firestore until the first message is sent. Hence, `currentChatId` locally in `ChatActivity` relies on observing the `chatIdResolved` StateFlow from the ViewModel. If this is missed, users cannot send their first message.
- **Role Race-Conditions**: The `ChatActivity` action bar rendering relies heavily on observing both `currentUserState` and `otherUserState`. Do not rely solely on the other user's role to determine UI visibility, otherwise deep links might momentarily display CA tooling (Proposal/Payment) to a Client while their own state still loads.
- **Layout Inflation Casts**: When creating items in `MessageAdapter` (`item_chat_sent` and `item_chat_received`), always cast complex wrapper views (e.g. video call chips) to the generic `View` class in the ViewHolders to avoid `ClassCastException` if XML properties change between a `LinearLayout` or a `MaterialCardView`.

## Key Files
| File | Role |
|---|---|
| `ChatActivity.kt` | Main chat screen, workflow UI, payment |
| `ChatViewModel.kt` | Message state, conversation state, file upload |
| `MessageAdapter.kt` | All message types (text, files, proposals, payments) |
| `ProposalDialogHelper.kt` | Proposal creation/payment request dialogs |
| `PaymentDialogHelper.kt` | Payment UX helpers |
| `ConversationModel.kt` | All STATE_* constants |
