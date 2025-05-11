# Koylel App Architecture

## Overview

Koylel is built using a hybrid architecture that combines elements of traditional Android development with Jetpack Compose UI components. The application follows these architectural principles:

## Core Components

### 1. Presentation Layer
- **MainActivity**: Central controller for the application, handling UI interactions, navigation, and coordinating between components.
- **UI Components**: Mix of traditional XML views and Jetpack Compose components.
- **Adapters**: `LogEntryAdapter` and `GeofenceAdapter` for displaying lists of data.

### 2. Service Layer
- **LocationMonitoringService**: Background service that manages location tracking and geofence monitoring.
- **CustomGeofenceBroadcastReceiver**: Handles geofence transition events.

### 3. Data Management
- **Local Storage**: SharedPreferences for app settings and user preferences.
- **JSON Import/Export**: Functionality for data backup and restoration.

### 4. Location Management
- **CustomGeofencingManager**: Manages geofence creation, monitoring, and events.
- **LocationDebugManager**: Provides debugging tools for location-based features.

## Data Flow

1. User interactions in the UI trigger events in MainActivity
2. MainActivity coordinates with services and managers
3. Location services monitor user position and trigger geofence events
4. Data is persisted locally using SharedPreferences
5. UI is updated based on data changes

## Dependencies

The app uses the following key dependencies:
- AndroidX Core and AppCompat libraries
- Jetpack Compose for UI components
- Google Play Services for location and geofencing
- Material Design components
- Kotlin Coroutines for asynchronous operations

## Future Architecture Improvements

### Suggested Enhancements
1. **MVVM Pattern Implementation**:
   - Add ViewModels to separate UI logic from data handling
   - Implement LiveData or StateFlow for reactive UI updates

2. **Repository Pattern**:
   - Create repositories to abstract data sources
   - Implement clean separation between data and business logic

3. **Dependency Injection**:
   - Consider implementing Hilt or Koin for dependency management
   - Improve testability and modularity

4. **Room Database**:
   - Replace SharedPreferences with Room for more robust data persistence
   - Enable more complex queries and relationships

5. **Modularization**:
   - Split the app into feature modules for better separation of concerns
   - Enable parallel development and improve build times 