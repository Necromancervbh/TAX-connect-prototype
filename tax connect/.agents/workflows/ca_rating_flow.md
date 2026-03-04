---
description: CA rating system — eligibility, submission, and aggregate update
---

# CA Rating Workflow

## Overview
Clients who have a COMPLETED booking with a CA can leave a star rating + optional review. Ratings are stored in Firestore and the CA's aggregate rating is updated atomically.

## Eligibility Gate
`DataRepository.checkRatingEligibility(userId, caId, callback)`:
- Queries `bookings` where `userId == userId`, `caId == caId`, `status == "COMPLETED"`.
- If any such booking exists → `isEligible = true`.
- Alternatively, checks `conversations` where the CA participated and state is NOT `REQUESTED` or `REFUSED`.

## Entry Points
1. **CADetailActivity** → "Write a Review" button (`btnRateCa`) — gated by `isUserEligibleToRate`.
2. **ChatActivity** → `showRateCaDialog()` auto-triggers when conversation reaches `STATE_COMPLETED` (only once per session via `hasShownRatingDialog` flag).

## Dialog
`dialog_rate_ca.xml` fragments:
- `RatingBar` — 1–5 stars
- `etFeedback` — optional review text (max 10 words)
- `btnSubmit` / `btnCancel`

## Submission: `DataRepository.addRating(rating, callback)`
1. Writes `RatingModel` to `users/{caId}/ratings/{reviewerId}` (keyed by reviewer UID — prevents duplicate ratings from same user).
2. Runs a Firestore transaction to recompute the CA's aggregate:
   ```
   newRating = ((oldRating × oldCount) + newStars) / newCount
   ```
3. Updates `users/{caId}.rating` and `users/{caId}.ratingCount`.

## Display
- `CAAdapter.bind()` — `chipRating` shows `"4.8"` or `"New"` for unrated CAs.
- `CADetailActivity.tvRatingStat` + `tvRating` — shows aggregate and review count.
- `CADetailActivity.rvRatings` — full list via `RatingAdapter`, ordered by timestamp descending.
- `UserModel.rating: Double` + `ratingCount: Int` — persisted to Firestore.

## Key Files
| File | Role |
|---|---|
| `DataRepository.kt` | `addRating`, `getRatings`, `checkRatingEligibility` |
| `RatingModel.kt` | Data class |
| `RatingAdapter.kt` | RecyclerView adapter for ratings list |
| `dialog_rate_ca.xml` | Rating dialog layout |
| `CADetailActivity.kt` | `showRateDialog()`, `loadRatings()`, `checkRatingEligibility()` |
| `ChatActivity.kt` | `showRateCaDialog()`, auto-triggered on COMPLETED state |
