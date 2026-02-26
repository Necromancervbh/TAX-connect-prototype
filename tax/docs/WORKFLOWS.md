# Application Workflows

This document outlines the core architectural workflows of the TaxConnect (MediaScroll) application. Consult this file to understand data flow and activity navigation.

## 1. Authentication Flow
**Entry Point:** `LoginActivity.kt` / `RegisterActivity.kt`
- **Logic:**
  - User enters credentials.
  - `DataRepository.loginUser()` authenticates via Firebase.
  - On success, `UserModel.role` is checked.
- **Navigation:**
  - `role == "CA"` -> `CADashboardActivity`
  - `role == "USER"` (Customer) -> `HomeActivity`

## 2. Chartered Accountant (CA) Workflow
**Core Hub:** `CADashboardActivity.kt` (`activity_ca_dashboard.xml`)
- **Structure:**
  - **Header:** Profile, Menu, Help.
  - **Quick Actions:**
    - "New Requests" -> `RequestsActivity`
    - "Bookings" -> `MyBookingsActivity`
    - "Wallet" -> `WalletActivity`
    - "Messages" -> `MyChatsActivity`
  - **Business Overview:**
    - Revenue Stats -> `BalanceSheetActivity`
    - Active Clients -> `MyChatsActivity`
    - Weekly Performance (Bar Chart)
  - **Community:** Networking -> `ExploreCAsActivity`
  - **Upcoming Bookings:** RecyclerView list of accepted bookings.

## 3. Customer (User) Workflow
**Core Hub:** `HomeActivity.kt` (`activity_home.xml`)
- **Structure:**
  - **Search:** Search bar for CAs.
  - **FAB:** Quick access to Search/Bookings.
  - **Featured/Top Rated:** RecyclerViews populated via `HomeViewModel`.
- **Discovery Flow:**
  - `HomeActivity` -> `ExploreCAsActivity` (Search/Filter) -> `CADetailActivity` -> Booking Process.

## 4. Booking System
- **Models:** `BookingModel`
- **Flow:**
  1. User initiates booking from `CADetailActivity`.
  2. Booking created with status `PENDING`.
  3. CA sees request in `RequestsActivity` or Dashboard Priority section.
  4. CA accepts -> Status `ACCEPTED` -> Moves to `MyBookingsActivity`.
  5. CA rejects -> Status `REJECTED`.

## 5. Data Layer
- **Repository:** `DataRepository.kt` (Singleton)
  - Handles all Firestore interactions.
  - Manages User profiles, Bookings, Wallet transactions.
- **ViewModels:**
  - `HomeViewModel`: Fetches CA lists for User Home.
  - `CADashboardViewModel`: Aggregates stats for CA Dashboard.

## 6. UI/UX Standards
- **Design System:** Material 3.
- **Typography:** Use theme attributes `?attr/textAppearance...` (e.g., `HeadlineMedium`, `BodyLarge`) instead of hardcoded sizes.
- **Colors:** Use theme attributes `?attr/colorOnSurface`, `?attr/colorSurfaceVariant` for text/backgrounds to ensure Dark Mode compatibility.
