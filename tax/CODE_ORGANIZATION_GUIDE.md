# TaxConnect Code Organization Guide

## Project Structure Overview

This document outlines the organized structure of the TaxConnect Android application, designed for maintainability, scalability, and ease of future modifications.

## Architecture Pattern

**MVVM with Clean Architecture + Repository Pattern**

- **Presentation Layer**: Activities, Fragments, ViewModels, UI Components
- **Domain Layer**: Use Cases, Business Logic
- **Data Layer**: Repositories, Data Sources, Models
- **Common Layer**: Shared utilities, constants, validation

## Package Structure

```
com.example.taxconnect/
├── common/                    # Shared utilities and constants
│   ├── Constants.kt          # App-wide constants
│   ├── validation/           # Input validation utilities
│   ├── error/               # Error handling utilities
│   ├── network/             # Network utilities
│   ├── preferences/         # SharedPreferences management
│   └── utils/               # General utility classes
├── data/                     # Data layer (repositories, data sources)
│   ├── base/                # Base repository classes
│   ├── local/               # Local data sources (Room, SharedPrefs)
│   ├── remote/              # Remote data sources (Firebase, APIs)
│   └── model/               # Data models
├── domain/                   # Domain layer (business logic)
│   ├── base/                # Base use case classes
│   ├── usecase/             # Use cases organized by feature
│   │   ├── auth/            # Authentication use cases
│   │   ├── wallet/          # Wallet use cases
│   │   ├── booking/         # Booking use cases
│   │   ├── user/            # User management use cases
│   └── notification/    # Notification use cases
│   └── model/               # Domain models
├── ui/                       # Presentation layer (UI)
│   ├── base/                # Base activity/viewmodel classes
│   ├── auth/                # Authentication UI
│   ├── wallet/              # Wallet UI
│   ├── booking/             # Booking UI
│   ├── profile/             # Profile UI
│   ├── dashboard/           # Dashboard UI
│   ├── explore/             # Explore CAs UI
│   ├── chat/                # Chat UI
│   └── components/          # Reusable UI components
├── navigation/               # Navigation utilities
├── di/                      # Dependency injection modules
└── backend/                  # Legacy backend (to be migrated)
    ├── repositories/         # Legacy repositories
    ├── models/               # Legacy models
    └── utils/                # Legacy utilities
```

## Key Components

### 1. Dependency Injection (Hilt)

**Location**: `di/AppModule.kt`

- Centralized dependency management
- Singleton instances for repositories and services
- Easy testing and mocking
- Lifecycle-aware components

### 2. Base Classes

**Location**: `ui/base/BaseActivity.kt`, `ui/base/BaseViewModel.kt`

- Common functionality across all activities
- View binding integration
- Error handling and loading states
- Lifecycle management

### 3. Use Cases

**Location**: `domain/usecase/`

- Single responsibility principle
- Reusable business logic
- Easy to test and maintain
- Consistent error handling

### 4. Navigation Manager

**Location**: `navigation/NavigationManager.kt`

- Centralized navigation logic
- Type-safe navigation
- Consistent parameter passing
- Easy to modify navigation flow

### 5. Validation Utilities

**Location**: `common/validation/ValidationUtils.kt`

- Input validation for all user inputs
- Email, password, phone validation
- File size and type validation
- Consistent error messages

### 6. Error Handling

**Location**: `common/error/ErrorHandler.kt`

- Centralized error handling
- Firebase-specific error mapping
- User-friendly error messages
- Network error detection

### 7. Preferences Management

**Location**: `common/preferences/PreferencesManager.kt`

- Type-safe preference access
- User settings management
- Cache management
- Feature flags

## Development Guidelines

### 1. Adding New Features

1. **Create Use Case**: Add to appropriate domain/usecase/ subdirectory
2. **Update Repository**: Add data access methods to relevant repository
3. **Create ViewModel**: Extend BaseViewModel in ui/ subdirectory
4. **Create UI**: Create activity/fragment in appropriate ui/ subdirectory
5. **Update Navigation**: Add navigation method to NavigationManager
6. **Add Constants**: Add any new constants to Constants.kt

### 2. Code Conventions

- **Naming**: Use descriptive names (e.g., `GetWalletBalanceUseCase`)
- **Packages**: Organize by feature, not by type
- **Error Handling**: Use Resource<T> for async operations
- **Testing**: Write unit tests for use cases and viewmodels
- **Documentation**: Document public APIs and complex logic

### 3. Best Practices

- **Single Responsibility**: Each class should have one clear purpose
- **Dependency Injection**: Use Hilt for all dependencies
- **Coroutines**: Use for all async operations
- **Error Propagation**: Always handle errors gracefully
- **Resource Management**: Properly clean up resources

### 4. Migration Strategy

The legacy `backend/` package contains existing code that should be gradually migrated:

1. **Phase 1**: Create new structure alongside existing code
2. **Phase 2**: Migrate one feature at a time
3. **Phase 3**: Update existing activities to use new structure
4. **Phase 4**: Remove legacy code once fully migrated

## Testing Strategy

### Unit Tests
- Use cases (business logic)
- ViewModels (UI state management)
- Utilities (validation, formatting)

### Integration Tests
- Repository methods
- Navigation flows
- Error handling

### UI Tests
- Critical user flows
- Edge cases
- Error scenarios

## Performance Considerations

### Memory Management
- Proper bitmap handling in FileUtils
- View binding cleanup in BaseActivity
- Repository lifecycle management

### Network Optimization
- Request batching
- Cache management
- Offline mode support

### Database Optimization
- Efficient queries
- Proper indexing
- Data pagination

## Security Considerations

### Data Protection
- Sensitive data in SharedPreferences is encrypted
- API keys stored in BuildConfig
- Proper file permissions

### Input Validation
- All user inputs are validated
- File uploads are type-checked
- SQL injection prevention

### Authentication
- Firebase Auth integration
- Session management
- Token refresh handling

## Future Enhancements

### Planned Features
- Room database integration
- Offline-first architecture
- Advanced caching strategies
- Biometric authentication
- Push notification improvements

### Scalability
- Modular feature structure
- Feature flags for A/B testing
- Analytics integration
- Crash reporting

This organization provides a solid foundation for future development while maintaining clean, testable, and maintainable code.

## Mandatory Architecture Rules (Updated 2026-02-16)

1. **Activity Inheritance**: All Activities MUST extend `com.example.taxconnect.ui.base.BaseActivity<VB>` where `VB` is the ViewBinding class.
   - Do NOT use `AppCompatActivity` directly.
   - Do NOT use the deprecated `frontend.activities.BaseActivity`.

2. **UI Interactions**:
   - MUST use `showToast(message)` from `BaseActivity` instead of `Toast.makeText()`.
   - `showToast()` automatically handles threading and context, with `Snackbar` fallback.

3. **Dependency Injection**:
   - All Activities MUST be annotated with `@AndroidEntryPoint`.
   - All ViewModels MUST be annotated with `@HiltViewModel` and use `@Inject constructor`.
   - Do NOT manually instantiate ViewModels or Repositories (except in legacy code during migration).

4. **Navigation**:
   - ALL navigation logic MUST be centralized in `com.example.taxconnect.navigation.NavigationManager`.
   - Do NOT create `Intent`s directly in Activities/Fragments.

5. **Clean Code**:
   - Remove unused imports (especially `android.widget.Toast`).
   - Remove unused resources and deprecated classes immediately.
