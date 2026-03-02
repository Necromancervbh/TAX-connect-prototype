# Changelog

This file tracks significant changes made to the codebase to maintain context and history.

## [Unreleased] - 2026-02-14

### Fixed
- **Critical Startup Crash**: Resolved deep-rooted crash during application initialization by adding robust error handling in `MyApplication.kt` and `SplashActivity.kt`.
- **Customer Login Crash**: Fixed crash when logging in as Customer by adding defensive error handling to `HomeActivity` initialization and `CAAdapter` layout inflation.
- **Theme Resource Error**: Fixed missing style references (`AppText.*.Tight`) in `themes.xml` that caused crashes during UI inflation.
- **Data Repository Stability**: Converted Firebase instances in `DataRepository` to lazy initialization to prevent crashes if `FirebaseApp` fails to initialize.
- **Login/Auth Stability**: Added error handling to `loginUser`, `registerUser`, and `fetchUser` to prevent crashes when Firebase services are unavailable.
- **Quick Action Buttons**: Fixed unresponsive Quick Action buttons on CA Dashboard by rebinding click listeners to visible CardViews.
- **Splash Screen**: Fixed crash on devices without biometric hardware by safely handling biometric checks.
- **UI Typography**: Standardized Material 3 typography references in themes.

### Changed
- **UI Text**: Updated "rugged" UI text in `activity_explore_cas.xml` and `activity_home.xml` to use modern Material 3 typography.
- **Biometrics**: Temporarily disabled strict biometric enforcement on startup to ensure smoother entry for all users.
