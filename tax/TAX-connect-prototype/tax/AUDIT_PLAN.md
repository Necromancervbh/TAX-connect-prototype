# Application Audit & Optimization Plan

## 1. Codebase Analysis
- [x] Search for TODOs/FIXMEs.
- [ ] Identify hardcoded strings.
- [ ] Check for unused resources (already done partially).
- [ ] Check for legacy Java files.

## 2. Functional Gaps Identified
- **ProfileActivity**: Image/Video upload logic is disjointed from Profile Update. Need to chain them.
- **WalletActivity**: Transaction list might be empty initially.
- **Chat**: Real-time updates need verification.

## 3. Testing Protocol
- **Unit Tests**: ViewModels are tested.
- **Manual Verification**:
    - [ ] Login/Register Flow
    - [ ] Home Screen (Top Rated, Featured)
    - [ ] CA Dashboard (Revenue, Stats)
    - [ ] Chat (Send/Receive)
    - [ ] Profile (Update, Upload)
    - [ ] Wallet (Deposit, Withdraw)

## 4. Optimization Tasks
- [ ] Implement Crashlytics.
- [ ] Add timber/logging.
- [ ] Fix Profile Upload flow.
- [ ] Check memory leaks (LeaksCanary - optional but good).

## 5. UI/UX
- [ ] Check Dark Mode consistency.
- [ ] Check Landscape mode.
