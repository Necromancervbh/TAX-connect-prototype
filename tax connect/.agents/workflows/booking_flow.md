---
description: End-to-end booking flow — how a client books a CA and how the CA accepts
---

# Booking Workflow

## Overview
Client selects a CA → books via `BookAppointmentActivity` (3-step stepper) → CA accepts in `MyBookingsActivity` → chat + booking are linked → `ChatActivity` opens.

## Step-by-Step

### 1. Client Side: Booking a CA
1. Client taps a CA card in `ExploreCAsActivity` → `CADetailActivity` opens.
2. Client taps **"Request Assistance"** → `BookAppointmentActivity` launches with `CA_DATA` extra.
3. **Step 1 — Service Selection**: Client picks a service from the CA's list (`rvServiceSelection`).
4. **Step 2 — Date & Time**: Client picks a date from the date strip and a time slot from the CA's availability.
5. **Step 3 — Summary & Confirm**: Client reviews details, optionally adds a note, taps **"Confirm Booking"**.
6. `processBooking()` creates a `BookingModel` with `status = "PENDING"` and calls `BookingRepository.saveBooking(booking)`.
7. On success, `showBookingSuccessDialog()` fires — client can tap **"View My Bookings"** to go to `MyBookingsActivity`.

### 2. CA Side: Accepting a Booking
1. CA sees the booking in `MyBookingsActivity` → **Pending** tab.
2. CA taps **Accept** → `onAccept()` calls `bookingRepo.acceptBookingWithChat(booking)`:
   - Sets `booking.status = "ACCEPTED"` in Firestore
   - Creates a new `ConversationModel` linked to the booking via `bookingId`.
   - **Important Integration Note:** It uses a deterministic ID (`uid1_uid2`) to ensure that only 1 chat history exists between these exact two users, eliminating duplicate or "shadow" chats.
   - Returns the new `chatId`
3. On success, `ChatActivity` launches immediately with `chatId`, `otherUserId`, `otherUserName`, and `bookingId`.

### 3. Chat Opens
1. `ChatActivity.initViews()` reads the `bookingId` intent extra and calls `fetchAndShowBookingContextBanner(bookingId)` — shows a snackbar with service name, date, time, and status.
2. Empty state is shown with booking-context greeting chips.
3. Client sends first message; the booking workflow progresses through **DISCUSSION → PROPOSAL → PAYMENT → COMPLETED**.

## Key Files
| File | Role |
|---|---|
| `BookAppointmentActivity.kt` | 3-step booking stepper |
| `BookingRepository.kt` | `saveBooking()`, `acceptBookingWithChat()` |
| `MyBookingsActivity.kt` | Booking list, accept/reject, mark complete |
| `BookingAdapter.kt` | Booking card with status chips |
| `ChatActivity.kt` | Chat screen, booking banner, workflow stepper |
| `ConversationRepository.kt` | Creates/listens to conversations |

## Status Transitions
```
PENDING → ACCEPTED (CA accepts) → [chat workflow] → COMPLETED
PENDING → REJECTED (CA declines)
ACCEPTED → CANCELLED
```
