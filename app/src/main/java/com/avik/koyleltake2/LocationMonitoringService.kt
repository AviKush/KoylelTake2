package com.avik.koyleltake2

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * A foreground service that monitors device location and checks for geofence transitions.
 * This implementation uses only Android's standard LocationManager APIs.
 */
class LocationMonitoringService : Service() {

    companion object {
        private const val TAG = "LocationMonitoringService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_monitoring_channel"
        private const val CHANNEL_NAME = "Location Monitoring"
        
        // Define action for broadcasting new log entries
        const val ACTION_NEW_LOG_ENTRY = "com.avik.koyleltake2.NEW_LOG_ENTRY"
        const val EXTRA_LOG_ENTRY = "log_entry"
        
        // Update intervals (in milliseconds)
        private const val LOCATION_UPDATE_INTERVAL = 15000L // 15 seconds
        private const val LOCATION_MIN_DISTANCE = 10f // 10 meters
        
        // Alarm for periodic restart
        private const val RESTART_ALARM_REQUEST_CODE = 1001
        
        // Singleton instance tracking - make public so it can be accessed from MainActivity
        @JvmStatic
        var isServiceRunning = false
        
        // Service state constants
        const val STATE_INACTIVE = 0  // Outside active hours
        const val STATE_ACTIVE = 1    // Active and tracking
        const val STATE_ENTRY_RECORDED = 2  // Entry already recorded for this period
    }
    
    private lateinit var locationManager: LocationManager
    private lateinit var customGeofencingManager: CustomGeofencingManager
    private lateinit var locationDebugManager: LocationDebugManager
    
    private var isTracking = false
    
    // Current service state
    private var serviceState = STATE_INACTIVE
    private var lastStateChangeTime = 0L
    private var lastLocationUpdateTime = 0L
    private var acquiringLocation = false
    
    // Location listener to receive location updates
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }
        
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // Not used, but need to override
        }
        
        @Deprecated("Deprecated in Java")
        override fun onProviderEnabled(provider: String) {
            // Try to request location updates again if provider becomes available
            if (!isTracking) {
                requestLocationUpdates()
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onProviderDisabled(provider: String) {
            // Provider disabled - we might need to switch providers
            if (isTracking) {
                requestLocationUpdates()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Service onCreate called")
        
        // Initialize managers
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        customGeofencingManager = CustomGeofencingManager(this)
        locationDebugManager = LocationDebugManager.getInstance(this)
        
        // Create notification channel for Android O+
        createNotificationChannel()
        
        // Start as a foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Clear any stale processing flags at startup
        setGeofenceProcessingFlag(false, 0)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand called with flags: $flags, startId: $startId")
        
        // Check if this is a manual start
        val isManualStart = intent?.getBooleanExtra("MANUAL_START", false) ?: false
        Log.d(TAG, "Manual start flag: $isManualStart")
        
        // Check if service is already running to prevent duplicate instances
        if (isServiceRunning) {
            Log.d(TAG, "Service already running, ignoring duplicate start request")
            
            // Check if we should look for recent manual entries
            if (intent?.getBooleanExtra("CHECK_MANUAL_ENTRIES", false) == true) {
                Log.d(TAG, "Checking for recent manual entries")
                checkForRecentManualEntries()
            }
            
            return START_STICKY
        }
        
        // Mark service as running
        isServiceRunning = true
        Log.d(TAG, "Service marked as running (singleton instance)")
        
        // Check if we have location permissions
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Location permissions not granted, can't run service properly")
            // Set state to inactive since we can't properly monitor
            updateServiceState(STATE_INACTIVE)
            // Even though permissions are missing, we'll keep the service running
            // It will try to get location when permissions are granted
            return START_STICKY
        }
        
        // Check if we're within active hours
        if (!isManualStart && !isWithinActiveHours()) {
            Log.d(TAG, "Outside active hours, scheduling restart for next period")
            // Set state to inactive
            updateServiceState(STATE_INACTIVE)
            scheduleServiceRestartForNextPeriod()
            return START_STICKY
        }
        
        // Check if an entry already exists for the current time period
        val currentPeriod = getCurrentTimePeriod()
        val hasEntry = hasEntryForCurrentPeriod(currentPeriod)
        
        if (hasEntry) {
            Log.d(TAG, "Entry already exists for current period: $currentPeriod")
            // Set state to entry recorded
            updateServiceState(STATE_ENTRY_RECORDED)
            // Schedule next check
            scheduleServiceRestartForNextPeriod()
            return START_STICKY
        }
        
        // We're in active hours, no entry yet - start location updates
        // Set state to active
        updateServiceState(STATE_ACTIVE)
        requestLocationUpdates()
        
        // If service is killed, restart it
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        
        // Stop location updates
        stopLocationUpdates()
        
        // Mark service as not running
        isServiceRunning = false
        Log.d(TAG, "Service destroyed and marked as not running")
        
        super.onDestroy()
    }
    
    /**
     * Create the required notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for location monitoring"
                lightColor = Color.BLUE
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create a persistent notification for the foreground service
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification based on current state
        return updateNotificationForCurrentState(
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_location_book)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
        ).build()
    }
    
    /**
     * Update the notification to reflect current service state
     */
    private fun updateNotificationForCurrentState(builder: NotificationCompat.Builder): NotificationCompat.Builder {
        val now = System.currentTimeMillis()
        val timeSinceLastLocationUpdate = if (lastLocationUpdateTime > 0) now - lastLocationUpdateTime else -1
        
        // Set title and content based on current state
        when (serviceState) {
            STATE_INACTIVE -> {
                // Outside active hours
                builder.setContentTitle(getString(R.string.location_monitoring_inactive))
                
                // Show when the next active period will be
                val nextPeriodInfo = getNextActivePeriodInfo()
                builder.setContentText(getString(R.string.location_monitoring_inactive_desc, nextPeriodInfo))
                builder.setColor(Color.GRAY)
            }
            
            STATE_ACTIVE -> {
                // Actively monitoring
                if (acquiringLocation) {
                    // Currently acquiring location
                    builder.setContentTitle(getString(R.string.location_monitoring_acquiring))
                    builder.setContentText(getString(R.string.location_monitoring_acquiring_desc))
                    builder.setColor(Color.GREEN)
                } else {
                    // Standard active monitoring
                    builder.setContentTitle(getString(R.string.location_monitoring_title))
                    
                    // Show time of last update if available
                    if (timeSinceLastLocationUpdate > 0) {
                        val lastUpdateMinutes = (timeSinceLastLocationUpdate / 60000).toInt()
                        builder.setContentText(getString(
                            R.string.location_monitoring_active_desc, 
                            lastUpdateMinutes
                        ))
                    } else {
                        builder.setContentText(getString(R.string.location_monitoring_text))
                    }
                    builder.setColor(Color.BLUE)
                }
            }
            
            STATE_ENTRY_RECORDED -> {
                // Entry already recorded for this period
                builder.setContentTitle(getString(R.string.location_monitoring_recorded))
                
                // Show when we recorded the entry
                val recordedTimeAgo = if (lastStateChangeTime > 0) {
                    val minutes = ((now - lastStateChangeTime) / 60000).toInt()
                    getString(R.string.location_monitoring_recorded_time, minutes)
                } else {
                    ""
                }
                
                // Show when the next check will happen
                val nextCheckInfo = getNextActivePeriodInfo()
                builder.setContentText(
                    getString(R.string.location_monitoring_recorded_desc, recordedTimeAgo, nextCheckInfo)
                )
                builder.setColor(Color.YELLOW)
            }
        }
        
        return builder
    }
    
    /**
     * Get information about the next active time period
     */
    private fun getNextActivePeriodInfo(): String {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when {
            // Morning period (9:00-13:00)
            currentHour < 9 -> {
                // Next period is today morning
                getString(R.string.today_morning)
            }
            currentHour < 15 -> {
                // Next period is today afternoon
                getString(R.string.today_afternoon)
            }
            else -> {
                // Next period is tomorrow morning
                getString(R.string.tomorrow_morning)
            }
        }
    }
    
    /**
     * Update the notification to reflect current state
     */
    private fun updateNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = updateNotificationForCurrentState(
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_location_book)
                    .setContentIntent(PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
            ).build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Updated notification for state: $serviceState")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
    
    /**
     * Update the service state and refresh the notification
     */
    private fun updateServiceState(newState: Int) {
        // Only update if state actually changed
        if (serviceState != newState) {
            val oldState = serviceState
            serviceState = newState
            lastStateChangeTime = System.currentTimeMillis()
            
            Log.d(TAG, "Service state changed from $oldState to $newState")
            
            // Update notification with new state
            updateNotification()
        }
    }
    
    /**
     * Request location updates from available providers
     */
    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }
        
        try {
            // Set acquiring location flag
            acquiringLocation = true
            updateNotification()
            
            // First check if GPS is enabled
            if (!isLocationEnabled()) {
                Log.e(TAG, "Location services are disabled")
                showLocationSettingsDialog()
                return
            }
            
            // Stop any existing updates
            stopLocationUpdates()
            
            // Debug log for location providers
            Log.d(TAG, "Available providers: " + locationManager.allProviders.joinToString())
            Log.d(TAG, "GPS enabled: ${locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)}")
            Log.d(TAG, "Network enabled: ${locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)}")
            
            // Try GPS provider first (most accurate)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_MIN_DISTANCE,
                    locationListener
                )
                Log.d(TAG, "Registered for GPS updates every $LOCATION_UPDATE_INTERVAL ms")
                isTracking = true
            }
            
            // Also try network provider for better indoor performance
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_MIN_DISTANCE,
                    locationListener
                )
                Log.d(TAG, "Registered for network location updates")
                isTracking = true
            }
            
            // If no provider is available, log an error
            if (!isTracking) {
                Log.e(TAG, "No location provider available")
                acquiringLocation = false
                updateNotification()
            } else {
                // Try to get a last known location immediately for faster response
                val lastLocation = getLastKnownLocation()
                lastLocation?.let { 
                    Log.d(TAG, "Using last known location for immediate update: ${it.latitude}, ${it.longitude}")
                    handleLocationUpdate(it) 
                }
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Error requesting location updates", e)
            acquiringLocation = false
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up location updates", e)
            acquiringLocation = false
            updateNotification()
        }
    }
    
    /**
     * Stop location updates
     */
    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
            isTracking = false
            acquiringLocation = false
            Log.d(TAG, "Location updates stopped")
            
            // Check if we're outside active hours or have recorded an entry
            val currentPeriod = getCurrentTimePeriod()
            val hasEntry = hasEntryForCurrentPeriod(currentPeriod)
            
            if (!isWithinActiveHours()) {
                // Outside active hours
                updateServiceState(STATE_INACTIVE)
            } else if (hasEntry) {
                // Entry already recorded
                updateServiceState(STATE_ENTRY_RECORDED)
            } else {
                // Active hours but no entry yet - remain in active state
                updateServiceState(STATE_ACTIVE)
            }
            
            // Schedule service to restart at the next period
            scheduleServiceRestartForNextPeriod()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }
    
    /**
     * Handle location updates and check for geofence transitions
     */
    private fun handleLocationUpdate(location: Location) {
        // Update timestamp
        lastLocationUpdateTime = System.currentTimeMillis()
        // Set acquiring location flag to false
        acquiringLocation = false
        
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
        
        // Check for nearest geofence and calculate distance
        val geofences = customGeofencingManager.getGeofences()
        
        // Add debug log for geofences
        Log.d(TAG, "Number of configured geofences: ${geofences.size}")
        geofences.forEach { geofence ->
            val distance = getDistanceTo(location, geofence.latitude, geofence.longitude)
            Log.d(TAG, "Geofence '${geofence.name}': distance=${distance}m, radius=${geofence.radius}m, isInside=${distance <= geofence.radius}")
        }
        
        val geofenceInfo = buildGeofenceDebugInfo(location, geofences)
        
        // Save to debug manager for troubleshooting
        locationDebugManager.addLocationEntry(location, geofenceInfo)
        
        // Check if we're in working hours
        if (!isWithinActiveHours()) {
            Log.d(TAG, "Outside active hours, not processing geofence")
            updateServiceState(STATE_INACTIVE)
            updateNotification()
            return
        }
        
        // Find if the user is inside any geofence
        var insideGeofence: CustomGeofencingManager.CustomGeofence? = null
        for (geofence in geofences) {
            val distance = getDistanceTo(location, geofence.latitude, geofence.longitude)
            if (distance <= geofence.radius) {
                insideGeofence = geofence
                Log.d(TAG, "User is INSIDE geofence: ${geofence.name}")
                break
            }
        }
        
        // If not inside any geofence, nothing to do
        if (insideGeofence == null) {
            Log.d(TAG, "Not inside any geofence")
            // Make sure notification is updated to show current state
            updateServiceState(STATE_ACTIVE)
            updateNotification()
            return
        }
        
        // Use a synchronized block with a static lock object to prevent concurrent execution
        synchronized(LocationMonitoringService::class.java) {
            // Double-check processing flag inside synchronized block
            if (isAlreadyProcessingGeofence()) {
                Log.d(TAG, "Already processing geofence entry - skipping to avoid duplicates")
                return
            }
            
            // Set processing flag with 30 second timeout
            setGeofenceProcessingFlag(true, 30000)
            
            try {
                // Check if an entry already exists for the current time period
                val currentPeriod = getCurrentTimePeriod()
                val hasEntry = hasEntryForCurrentPeriod(currentPeriod)
                Log.d(TAG, "Current period: $currentPeriod, has existing entry: $hasEntry")
                
                if (hasEntry) {
                    Log.d(TAG, "Entry already exists for current period: $currentPeriod")
                    // Update state to entry recorded
                    updateServiceState(STATE_ENTRY_RECORDED)
                    // Stop updates until next period
                    stopLocationUpdates()
                    return
                }
                
                // User is inside a geofence during working hours and no entry exists for this period
                // Create an entry
                Log.d(TAG, "Creating entry for geofence: ${insideGeofence.name} in period: $currentPeriod")
                createGeofenceEntry(insideGeofence)
                
                // Update state to entry recorded
                updateServiceState(STATE_ENTRY_RECORDED)
                
                // Stop location updates until next period
                stopLocationUpdates()
            } finally {
                // Always clear the flag when we're done processing
                setGeofenceProcessingFlag(false, 0)
            }
        }
    }
    
    /**
     * Build debug information about nearby geofences
     */
    private fun buildGeofenceDebugInfo(location: Location, geofences: List<CustomGeofencingManager.CustomGeofence>): String {
        if (geofences.isEmpty()) {
            return "No geofences configured"
        }
        
        val nearestGeofence = geofences.minByOrNull { 
            getDistanceTo(location, it.latitude, it.longitude)
        }
        
        nearestGeofence?.let {
            val distance = getDistanceTo(location, it.latitude, it.longitude)
            val isInside = distance <= it.radius
            
            return if (isInside) {
                "INSIDE '${it.name}' (${distance.toInt()}m of ${it.radius.toInt()}m radius)"
            } else {
                "OUTSIDE '${it.name}' (${distance.toInt()}m away, radius ${it.radius.toInt()}m)"
            }
        }
        
        return "Geofence data unavailable"
    }
    
    /**
     * Calculate distance between two points
     */
    private fun getDistanceTo(location: Location, latitude: Double, longitude: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            latitude, longitude,
            results
        )
        return results[0]
    }
    
    /**
     * Check if there's already an entry for the current time period
     */
    private fun hasEntryForCurrentPeriod(period: TimePeriod): Boolean {
        val calendar = Calendar.getInstance()
        val today = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        Log.d(TAG, "Checking for existing entries for today (${getFormattedTimestamp(today)}) and period: $period")
        
        // Shared preferences to access log entries
        val sharedPreferences = getSharedPreferences("log_entries", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("log_entries_json", null)
        if (jsonString == null) {
            Log.d(TAG, "No entries found in log_entries_json")
            return false
        }
        
        try {
            val jsonArray = JSONArray(jsonString)
            Log.d(TAG, "Found ${jsonArray.length()} total entries")
            
            for (i in 0 until jsonArray.length()) {
                val entryObj = jsonArray.getJSONObject(i)
                val timestamp = entryObj.getLong("id")
                val type = if (entryObj.has("type")) 
                    LogEntryType.valueOf(entryObj.getString("type")) 
                else 
                    LogEntryType.NORMAL
                
                // Check both geofence entries and manually added entries (NORMAL type)
                // Skip only car rides (LogEntryType.CAR)
                if (type == LogEntryType.CAR) {
                    Log.d(TAG, "Skipping car entry from ${getFormattedTimestamp(timestamp)}")
                    continue
                }
                
                // Check if entry is from today
                if (timestamp < today) {
                    Log.d(TAG, "Skipping entry from previous day: ${getFormattedTimestamp(timestamp)}")
                    continue
                }
                
                // Get the hour of the entry
                val entryCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                val entryHour = entryCalendar.get(Calendar.HOUR_OF_DAY)
                
                // Check if entry is from the same period
                val entryPeriod = when (entryHour) {
                    in 9..12 -> TimePeriod.MORNING
                    in 15..18 -> TimePeriod.AFTERNOON
                    else -> TimePeriod.NONE
                }
                
                Log.d(TAG, "Entry from ${getFormattedTimestamp(timestamp)}, hour: $entryHour, period: $entryPeriod, type: $type")
                
                if (entryPeriod == period) {
                    val locationName = if (entryObj.has("locationName")) entryObj.getString("locationName") else "manually added"
                    Log.d(TAG, "Found matching entry for period $period: $locationName (type: $type)")
                    return true // Found an entry for this period
                }
            }
            
            Log.d(TAG, "No matching entries found for period: $period")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for existing entries", e)
        }
        
        return false // No entry found for current period
    }
    
    /**
     * Get the current time period (morning, afternoon, or none)
     */
    private enum class TimePeriod { MORNING, AFTERNOON, NONE }
    
    private fun getCurrentTimePeriod(): TimePeriod {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 9..12 -> TimePeriod.MORNING
            in 15..18 -> TimePeriod.AFTERNOON
            else -> TimePeriod.NONE
        }
    }
    
    /**
     * Create a geofence entry
     */
    private fun createGeofenceEntry(geofence: CustomGeofencingManager.CustomGeofence) {
        // Create timestamp
        val currentTime = System.currentTimeMillis()
        
        // Determine seder based on hour
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val seder = when (hour) {
            in 9..12 -> "'סדר א"
            in 15..18 -> "'סדר ב"
            else -> ""
        }
        
        // Format the location name with seder if applicable
        val formattedLocationName = if (seder.isNotEmpty()) {
            "${geofence.name} $seder"
        } else {
            geofence.name
        }
        
        // Create log entry
        val logEntry = LogEntry(
            id = currentTime,
            timestamp = getFormattedTimestamp(currentTime),
            type = LogEntryType.GEOFENCE,
            locationName = formattedLocationName
        )
        
        // Save the entry
        saveLogEntry(logEntry)
        
        // Show a toast notification
        val notificationIntent = Intent(ACTION_NEW_LOG_ENTRY)
        notificationIntent.putExtra(EXTRA_LOG_ENTRY, logEntry)
        sendBroadcast(notificationIntent)
        
        // Log the creation
        Log.d(TAG, "Created geofence entry for ${geofence.name} with seder $seder")
    }
    
    /**
     * Format the timestamp
     */
    private fun getFormattedTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Save a log entry to shared preferences
     */
    private fun saveLogEntry(logEntry: LogEntry) {
        val sharedPreferences = getSharedPreferences("log_entries", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("log_entries_json", null)
        val jsonArray = if (jsonString != null) {
            JSONArray(jsonString)
        } else {
            JSONArray()
        }
        
        // Add new entry
        val obj = JSONObject()
        obj.put("id", logEntry.id)
        obj.put("timestamp", logEntry.timestamp)
        obj.put("type", logEntry.type.name)
        logEntry.amount?.let { obj.put("amount", it) }
        logEntry.locationName?.let { obj.put("locationName", it) }
        jsonArray.put(obj)
        
        // Save to preferences
        sharedPreferences.edit()
            .putString("log_entries_json", jsonArray.toString())
            .apply()
    }
    
    /**
     * Check if current time is within active hours
     */
    private fun isWithinActiveHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Log for debugging
        Log.d(TAG, "Current day: $dayOfWeek, hour: $hour")
        
        // FOR TESTING ONLY: Return true to test at any time
        return true;
        
        // Regular implementation (commented out during testing)
        /*
        // Check if it's Sunday (1) through Thursday (5)
        if (dayOfWeek < Calendar.SUNDAY || dayOfWeek > Calendar.THURSDAY) {
            return false
        }
        
        // Check if it's between 9-13 or 15-19
        return (hour in 9..12) || (hour in 15..18)
        */
    }
    
    /**
     * Check if location services are enabled
     */
    private fun isLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            gpsEnabled || networkEnabled
        }
    }
    
    /**
     * Show dialog to enable location settings
     */
    private fun showLocationSettingsDialog() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    /**
     * Schedule the service to restart at the next time period
     */
    private fun scheduleServiceRestartForNextPeriod() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Set restart time based on current time
        if (currentHour < 9) {
            // Before morning period, restart at 9:00 AM
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
        } else if (currentHour < 15) {
            // Before afternoon period, restart at 15:00 (3:00 PM)
            calendar.set(Calendar.HOUR_OF_DAY, 15)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
        } else {
            // After afternoon period, restart at 9:00 AM the next day
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
        }
        
        // Create an alarm to restart the service
        val restartTime = calendar.timeInMillis
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, LocationMonitoringService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            RESTART_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                restartTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                restartTime,
                pendingIntent
            )
        }
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "Scheduled service restart for: ${sdf.format(Date(restartTime))}")
    }
    
    /**
     * Get the best last known location from available providers
     */
    private fun getLastKnownLocation(): Location? {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            val lastGpsLocation = if (isGpsEnabled) 
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val lastNetworkLocation = if (isNetworkEnabled) 
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            
            // Use the most recent location
            return when {
                lastGpsLocation != null && lastNetworkLocation != null -> 
                    if (lastGpsLocation.time > lastNetworkLocation.time) lastGpsLocation else lastNetworkLocation
                lastGpsLocation != null -> lastGpsLocation
                lastNetworkLocation != null -> lastNetworkLocation
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            return null
        }
    }
    
    /**
     * Set the geofence processing flag with a timeout
     */
    private fun setGeofenceProcessingFlag(isProcessing: Boolean, timeoutMs: Long = 0) {
        val prefs = getSharedPreferences("geofence_processing", Context.MODE_PRIVATE)
        val timestamp = if (isProcessing && timeoutMs > 0) {
            System.currentTimeMillis() + timeoutMs  // Set expiration time
        } else {
            0L  // No expiration
        }
        
        prefs.edit()
            .putBoolean("is_processing", isProcessing)
            .putLong("processing_timestamp", timestamp)
            .commit()
        
        Log.d(TAG, "Set geofence processing flag to: $isProcessing" + 
              if (timeoutMs > 0) " with ${timeoutMs}ms timeout" else "")
    }
    
    /**
     * Check if we're already processing a geofence to avoid duplicate entries
     */
    private fun isAlreadyProcessingGeofence(): Boolean {
        val prefs = getSharedPreferences("geofence_processing", Context.MODE_PRIVATE)
        val isProcessing = prefs.getBoolean("is_processing", false)
        
        if (!isProcessing) {
            return false
        }
        
        // Check for timeout
        val expirationTime = prefs.getLong("processing_timestamp", 0)
        val now = System.currentTimeMillis()
        
        // If there's an expiration time and it's passed, clear the flag
        if (expirationTime > 0 && now > expirationTime) {
            Log.d(TAG, "Found expired processing flag, clearing it")
            setGeofenceProcessingFlag(false)
            return false
        }
        
        return true
    }
    
    /**
     * Check if we have the necessary location permissions
     */
    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check for recent manually added entries and update service state if found
     */
    private fun checkForRecentManualEntries() {
        // Get current time period
        val currentPeriod = getCurrentTimePeriod()
        if (currentPeriod == TimePeriod.NONE) {
            Log.d(TAG, "Current time is outside active periods, no need to check for manual entries")
            return
        }
        
        // Check if there's an entry for the current period
        val hasEntry = hasEntryForCurrentPeriod(currentPeriod)
        
        if (hasEntry) {
            Log.d(TAG, "Manual entry found for current period: $currentPeriod")
            
            // If we were actively tracking, stop location updates
            if (isTracking) {
                Log.d(TAG, "Stopping location updates since manual entry exists")
                stopLocationUpdates()
            }
            
            // Update state to entry recorded
            updateServiceState(STATE_ENTRY_RECORDED)
            
            // Update notification
            updateNotification()
            
            // Schedule for next period
            scheduleServiceRestartForNextPeriod()
        } else {
            Log.d(TAG, "No manual entry found for current period: $currentPeriod")
            
            // If we're in active state but not tracking, start tracking
            if (serviceState == STATE_ACTIVE && !isTracking) {
                Log.d(TAG, "Resuming location updates")
                requestLocationUpdates()
            }
        }
    }
} 