# Koylel App Features

## Core Features

### Time Tracking
- **Automatic Time Logging**: Automatically detects when users enter designated study locations and logs their presence
- **Manual Time Entry**: Allows users to manually add learning session timestamps
- **Seder Categorization**: Automatically categorizes entries into Seder Alef (morning) or Seder Bet (afternoon) based on time of day
- **Entry Management**: View, edit, and delete time log entries

### Location Management
- **Geofence Creation**: Create and configure virtual boundaries around study locations
- **Location Detection**: Uses Android's geofencing API to detect when users enter or exit defined areas
- **Custom Naming**: Assign meaningful names to frequently visited locations
- **Radius Configuration**: Adjust detection radius for each location to improve accuracy

### Vehicle Usage Tracking
- **Car Entry Logging**: Dedicated button for logging car usage separately from study sessions
- **Car Entry Summary**: View total number of car entries with option to delete all
- **Toggle Visibility**: Option to show/hide the car tracking button based on user needs

### Data Management
- **JSON Export**: Export all time logs to a JSON file for backup
- **JSON Import**: Restore time logs from previously exported JSON files
- **Data Clearing**: Options to clear all entries or just car entries
- **Persistence**: Automatic saving of entries to device storage

### UI and Customization
- **Navigation Drawer**: Slide-out menu providing access to all app features
- **Color Themes**: Customize main color, background colors, and text color
- **Language Support**: Full bilingual support for English and Hebrew
- **Hebrew Calendar**: Display dates in the Hebrew calendar format

### Background Services
- **Background Location Monitoring**: Continues tracking location even when the app is not in the foreground
- **Battery Optimization**: Efficient location monitoring to minimize battery usage
- **Notification Controls**: Enable/disable the background service with notifications

### Debugging Tools
- **Location Debug Information**: View detailed information about current location and geofence status
- **Debug Fragment**: Dedicated UI for troubleshooting location-related issues

## Planned Features
- **Statistics and Reports**: Generate reports of study time and patterns
- **Cloud Sync**: Optional synchronization with cloud storage
- **Sharing**: Share study logs with others (e.g., teachers or study partners)
- **Reminders**: Set reminders for scheduled study sessions 