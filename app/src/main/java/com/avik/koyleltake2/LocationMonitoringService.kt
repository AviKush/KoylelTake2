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
    }
    
    private lateinit var locationManager: LocationManager
    private lateinit var customGeofencingManager: CustomGeofencingManager
    private lateinit var locationDebugManager: LocationDebugManager
    
    private var isTracking = false
    
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
        
        // Initialize managers
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        customGeofencingManager = CustomGeofencingManager(this)
        locationDebugManager = LocationDebugManager.getInstance(this)
        
        // Create notification channel for Android O+
        createNotificationChannel()
        
        // Start as a foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        // Check if we're within active hours
        if (!isWithinActiveHours()) {
            Log.d(TAG, "Outside active hours, pausing location updates")
            stopLocationUpdates()
            return START_STICKY
        }
        
        // Request location updates
        requestLocationUpdates()
        
        // If service is killed, restart it
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop location updates
        stopLocationUpdates()
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
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_monitoring_title))
            .setContentText(getString(R.string.location_monitoring_text))
            .setSmallIcon(R.drawable.ic_stand)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
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
            // First check if GPS is enabled
            if (!isLocationEnabled()) {
                showLocationSettingsDialog()
                return
            }
            
            // Stop any existing updates
            stopLocationUpdates()
            
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
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Error requesting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up location updates", e)
        }
    }
    
    /**
     * Stop location updates
     */
    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
            isTracking = false
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }
    
    /**
     * Handle location updates and check for geofence transitions
     */
    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
        
        // Check for nearest geofence and calculate distance
        val geofences = customGeofencingManager.getGeofences()
        val geofenceInfo = buildGeofenceDebugInfo(location, geofences)
        
        // Save to debug manager
        locationDebugManager.addLocationEntry(location, geofenceInfo)
        
        // Process the location through our geofencing manager
        val result = customGeofencingManager.processLocation(location)
        
        // If the geofence manager detected a transition, handle it
        if (result.transitionDetected) {
            handleGeofenceTransition(result.geofenceId, result.transitionType)
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
     * Handle a geofence transition event
     */
    private fun handleGeofenceTransition(geofenceId: String, transitionType: Int) {
        Log.d(TAG, "Geofence transition: $geofenceId, type: $transitionType")
        
        // Only handle "enter" transitions (defined as constant 1 in CustomGeofencingManager)
        if (transitionType == CustomGeofencingManager.GEOFENCE_TRANSITION_ENTER) {
            // Create and save a log entry using the original LogEntry structure
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = sdf.format(Date())
            
            val entry = LogEntry(
                id = System.currentTimeMillis(),
                timestamp = timestamp,
                type = LogEntryType.GEOFENCE,
                locationName = geofenceId
            )
            
            // Send broadcast with the entry
            val intent = Intent(ACTION_NEW_LOG_ENTRY)
            intent.putExtra(EXTRA_LOG_ENTRY, entry)
            sendBroadcast(intent)
            
            // Pause location updates until next period
            stopLocationUpdates()
        }
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
     * Check if current time is within active hours
     */
    private fun isWithinActiveHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Check if it's Sunday (1) through Thursday (5)
        if (dayOfWeek < Calendar.SUNDAY || dayOfWeek > Calendar.THURSDAY) {
            return false
        }
        
        // Check if it's between 9-13 or 15-19
        return (hour in 9..12) || (hour in 15..18)
    }
} 