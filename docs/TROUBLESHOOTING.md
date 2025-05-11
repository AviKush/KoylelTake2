# Troubleshooting Guide for Koylel

This document provides solutions for common issues you might encounter while using the Koylel app.

## Location Tracking Issues

### Location Not Being Detected

**Symptoms:**
- App doesn't log entries when entering study locations
- No geofence triggers are being received

**Possible Solutions:**
1. **Check Permissions:**
   - Ensure location permissions are granted (Settings > Apps > Koylel > Permissions)
   - Background location permission must be explicitly granted
   - On Android 12+, make sure "Precise location" is enabled

2. **Battery Optimization:**
   - Disable battery optimization for Koylel (Settings > Apps > Koylel > Battery > Unrestricted)
   - Some devices have additional battery saving modes that restrict background services

3. **Location Services:**
   - Verify location services are enabled on your device
   - Try setting location mode to "High accuracy"
   - Restart location services on your device

4. **App Settings:**
   - Make sure the location monitoring service is enabled in the app
   - Try restarting the app
   - Check if geofences are properly configured (radius not too small)

### Excessive Battery Drain

**Symptoms:**
- Battery depletes quickly when using the app
- Phone feels warm when app is running

**Possible Solutions:**
1. **Adjust Location Settings:**
   - Increase geofence radius to reduce the frequency of location checks
   - Check for overlapping geofences that might cause multiple triggers

2. **Service Settings:**
   - Disable the location service when not needed
   - Restart the app or device

## Data Management Issues

### Missing Entries

**Symptoms:**
- Time logs disappear or are not saved
- Entries are incomplete

**Possible Solutions:**
1. **Storage Permissions:**
   - Ensure the app has storage permissions

2. **Data Recovery:**
   - Check if you have a recent JSON backup
   - Import the backup using the app's import feature

3. **App Data:**
   - Clear the app's cache (not data) and restart

### Import/Export Problems

**Symptoms:**
- Unable to export or import JSON data
- Export/import operation fails or crashes

**Possible Solutions:**
1. **File Access:**
   - Grant file access permissions when prompted
   - Try saving to a different location

2. **File Format:**
   - Ensure imported files are in the correct JSON format
   - Check for corruption in the JSON file

## UI and Language Issues

### Text Display Problems

**Symptoms:**
- Hebrew text appears garbled or reversed
- UI elements overlap or are misaligned

**Possible Solutions:**
1. **Language Settings:**
   - Try switching language and then switching back
   - Restart the app after changing language

2. **Device Settings:**
   - Check if your device fully supports RTL languages
   - Update your system to the latest version

### Navigation Drawer Issues

**Symptoms:**
- Navigation drawer doesn't open or close properly
- Items in the drawer are not responsive

**Possible Solutions:**
1. **App Restart:**
   - Force close and restart the app
   - Clear app cache

2. **Device Restart:**
   - Restart your device

## Notification Issues

### Missing Notifications

**Symptoms:**
- No notifications when entering/exiting geofences
- Service notifications disappear

**Possible Solutions:**
1. **Notification Permissions:**
   - Check notification permissions in system settings
   - Ensure notifications are not blocked or silenced

2. **Battery Settings:**
   - Disable battery optimization for the app
   - Allow app to run in background

## Technical Troubleshooting

### Debug Mode

To enable debug mode for detailed logging:
1. Go to Settings in the app
2. Tap on "About" 7 times quickly
3. Debug options will appear
4. Enable "Location Debug"

### Log Collection

To collect logs for troubleshooting:
1. Enable debug mode
2. Reproduce the issue
3. Go to Debug section in the app
4. Use "Export Debug Logs" option
5. Share the logs with the developer

## Contact Support

If you've tried the solutions above and still experience issues:
- Email: support@koylel.app
- Include your device model, Android version, and app version
- Describe the steps to reproduce the issue
- Attach screenshots or logs if possible 