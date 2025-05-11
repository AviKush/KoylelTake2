# Koylel - Yeshiva Time Tracking App

## Overview
Koylel is a specialized time tracking application designed for Yeshiva students to log their learning sessions and location-based attendance. The app automatically detects when a user enters specific locations (like a Beit Midrash) and logs their attendance with appropriate Seder designations.

## Features

### Core Functionality
- **Automatic Time Logging**: Records when you enter designated study locations
- **Manual Time Entry**: Add learning session timestamps manually
- **Seder Categorization**: Automatically categorizes entries into Seder Alef (morning) or Seder Bet (afternoon) based on time
- **Location-Based Tracking**: Uses geofencing to detect when you enter study locations
- **Vehicle Tracking**: Track car usage separately with dedicated entries

### Location Features
- **Geofence Management**: Set up and manage locations for automatic tracking
- **Custom Location Names**: Name your important study locations
- **Location Radius Control**: Adjust the detection radius for each location
- **Stand Icon**: Special "סטנדר" (learning stand) icon for location entries

### Data Management
- **JSON Export/Import**: Backup and restore your time logs
- **Clear Data**: Option to clear all entries or just car entries
- **Car Entries Summary**: View total number of car entries with option to delete all

### Customization
- **Color Themes**: Customize main color, background colors, and text color
- **Language Support**: Full support for both English and Hebrew
- **Car Button Toggle**: Show/hide the car tracking button based on your needs

### UI Features
- **Clean Interface**: Modern, material design interface
- **Navigation Drawer**: Easy access to all features via slide-out menu
- **Hebrew Calendar Integration**: Displays dates in the Hebrew calendar format
- **Debug Tools**: Location debug information for troubleshooting

## How to Use

### Time Tracking
1. **Automatic Tracking**: The app will automatically log your entry when you arrive at a configured location
2. **Manual Entry**: Tap the "+" button to log your current time, or use the menu to add a specific time
3. **Car Usage**: Tap the car button (if enabled) to log car usage

### Setting Up Locations
1. Open the navigation drawer by tapping the hamburger menu
2. Select "Manage Locations"
3. Tap "Add Location" to create a new geofence
4. Enter a name, radius, and either use your current location or enter coordinates

### Customization
- **Colors**: Access the color customization through the navigation drawer
- **Language**: Switch between English and Hebrew from the language menu
- **Car Button**: Toggle visibility of the car button in the Car Entries summary dialog

### Data Management
- **Export**: Back up your data using the Export to JSON option
- **Import**: Restore your data using the Import JSON option
- **Clear Data**: Use Clear All to reset, or selectively clear car entries

## Technical Details
The app uses Android's location services and geofencing APIs to provide accurate location-based tracking. It runs a background service to monitor location while minimizing battery usage.

Koylel respects your privacy by keeping all your data on your device unless you explicitly choose to export it.

## Privacy Permissions
- **Location**: Required for geofencing features (including background location)
- **Foreground Service**: Used for reliable location monitoring

## Support
For support or feature requests, please contact the developer.

---

*Note: This app is designed specifically for the unique scheduling needs of Yeshiva students. The name "Koylel" references Kollel, an institute for full-time, advanced study of the Talmud and rabbinic literature.* 