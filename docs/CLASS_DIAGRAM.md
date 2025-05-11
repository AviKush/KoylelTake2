# Koylel App Class Diagram

## Current Architecture

```mermaid
classDiagram
    class MainActivity {
        -logEntries: List~LogEntry~
        -customGeofencingManager: CustomGeofencingManager
        -locationManager: LocationManager
        +onCreate()
        +onCreateOptionsMenu()
        +addLogEntry()
        +exportToJson()
        +importFromJson()
        +manageLocations()
        +toggleLocationService()
    }
    
    class LogEntry {
        -timestamp: Long
        -isCarEntry: Boolean
        -locationName: String
        +getFormattedTime(): String
    }
    
    class LogEntryAdapter {
        -logEntries: List~LogEntry~
        -context: Context
        +onCreateViewHolder()
        +onBindViewHolder()
        +getItemCount()
    }
    
    class LocationMonitoringService {
        -locationManager: LocationManager
        -customGeofencingManager: CustomGeofencingManager
        +onCreate()
        +onStartCommand()
        +onDestroy()
        +requestLocationUpdates()
        +createNotificationChannel()
    }
    
    class CustomGeofencingManager {
        -geofencingClient: GeofencingClient
        -geofenceList: List~Geofence~
        -context: Context
        +addGeofence()
        +removeGeofence()
        +getGeofenceTransitionDetails()
        +loadGeofences()
        +saveGeofences()
    }
    
    class CustomGeofenceBroadcastReceiver {
        +onReceive()
        -handleGeofenceTransition()
    }
    
    class LocationDebugFragment {
        -locationManager: LocationManager
        -debugManager: LocationDebugManager
        +onCreateView()
        +updateDebugInfo()
    }
    
    class LocationDebugManager {
        -context: Context
        +getLocationInfo(): String
        +getGeofenceInfo(): String
    }
    
    class MyApplication {
        +onCreate()
        +getAppContext(): Context
    }
    
    MainActivity --> LogEntry : creates/manages
    MainActivity --> LogEntryAdapter : creates/uses
    MainActivity --> CustomGeofencingManager : uses
    MainActivity --> LocationMonitoringService : starts/stops
    LocationMonitoringService --> CustomGeofencingManager : uses
    CustomGeofenceBroadcastReceiver --> CustomGeofencingManager : uses
    MainActivity --> LocationDebugFragment : creates
    LocationDebugFragment --> LocationDebugManager : uses
    MyApplication ..> MainActivity : application context
```

## Proposed Architecture (MVVM)

```mermaid
classDiagram
    class MainActivity {
        -viewModel: MainViewModel
        +onCreate()
        +setupUI()
        +setupObservers()
    }
    
    class MainViewModel {
        -repository: LogEntryRepository
        -locationManager: LocationManager
        -entries: LiveData~List~LogEntry~~
        +addLogEntry()
        +deleteLogEntry()
        +exportEntries()
        +importEntries()
        +toggleLocationService()
    }
    
    class LogEntryRepository {
        -localDataSource: LogEntryLocalDataSource
        +getEntries(): Flow~List~LogEntry~~
        +addEntry()
        +deleteEntry()
        +clearEntries()
        +exportToJson(): String
        +importFromJson()
    }
    
    class LogEntryLocalDataSource {
        -sharedPreferences: SharedPreferences
        +getEntries(): List~LogEntry~
        +saveEntry()
        +deleteEntry()
        +clearEntries()
    }
    
    class LogEntry {
        -id: UUID
        -timestamp: Long
        -isCarEntry: Boolean
        -locationName: String
        +getFormattedTime(): String
    }
    
    class LogEntryAdapter {
        -entries: List~LogEntry~
        -onItemClick: (LogEntry) -> Unit
        +onCreateViewHolder()
        +onBindViewHolder()
        +submitList()
    }
    
    class LocationService {
        -locationRepository: LocationRepository
        +startLocationMonitoring()
        +stopLocationMonitoring()
        +createNotification()
    }
    
    class LocationRepository {
        -geofencingManager: GeofencingManager
        -locationDataSource: LocationDataSource
        +getCurrentLocation(): Flow~Location~
        +getGeofences(): Flow~List~Geofence~~
        +addGeofence()
        +removeGeofence()
    }
    
    class GeofencingManager {
        -geofencingClient: GeofencingClient
        +addGeofence()
        +removeGeofence()
        +handleGeofenceTransition()
    }
    
    class GeofenceBroadcastReceiver {
        +onReceive()
    }
    
    MainActivity --> MainViewModel : observes
    MainViewModel --> LogEntryRepository : uses
    LogEntryRepository --> LogEntryLocalDataSource : uses
    MainActivity --> LogEntryAdapter : uses
    MainViewModel --> LocationRepository : uses
    LocationService --> LocationRepository : uses
    LocationRepository --> GeofencingManager : uses
    GeofenceBroadcastReceiver --> GeofencingManager : calls
```

The proposed architecture follows MVVM principles with clear separation of concerns:

1. **View Layer**: Activities and Fragments that observe ViewModels
2. **ViewModel Layer**: Holds UI state and business logic
3. **Repository Layer**: Single source of truth for data
4. **Data Sources**: Local and remote data providers

This structure improves testability, maintainability, and follows Android architecture best practices. 