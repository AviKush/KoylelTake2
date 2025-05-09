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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/**
 * A foreground service that monitors device location and checks for geofence transitions.
 */
class LocationMonitoringService : Service() {

    companion object {
        private const val TAG = "LocationMonitoringService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "geofence_channel"
        
        // Update intervals
        private const val LOCATION_UPDATE_INTERVAL = 60000L // 1 minute
        private const val LOCATION_FASTEST_INTERVAL = 30000L // 30 seconds
        private const val LOCATION_DISTANCE_THRESHOLD = 10f // 10 meters
    }
    
    private lateinit var locationManager: LocationManager
    private lateinit var customGeofencingManager: CustomGeofencingManager
    
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
            requestLocationUpdates()
        }
        
        @Deprecated("Deprecated in Java")
        override fun onProviderDisabled(provider: String) {
            // Provider disabled - we might need to switch providers
            requestLocationUpdates()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize managers
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        customGeofencingManager = CustomGeofencingManager(this)
        
        // Create notification channel for Android O+
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as a foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
        
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
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }
    
    /**
     * Create the required notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geofence Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for geofence monitoring"
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
            // Try GPS provider first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_DISTANCE_THRESHOLD,
                    locationListener
                )
                Log.d(TAG, "Registered for GPS updates")
            }
            
            // Also try network provider for better indoor performance
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_DISTANCE_THRESHOLD,
                    locationListener
                )
                Log.d(TAG, "Registered for network updates")
            }
            
            // Passive provider as a fallback (receives updates triggered by other apps)
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_DISTANCE_THRESHOLD,
                    locationListener
                )
                Log.d(TAG, "Registered for passive updates")
            }
            
            // If no provider is available, log an error
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                Log.e(TAG, "No location provider available")
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Error requesting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up location updates", e)
        }
    }
    
    /**
     * Handle location updates and check for geofence transitions
     */
    private fun handleLocationUpdate(location: Location) {
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
        
        // Process the location through our geofencing manager
        customGeofencingManager.processLocation(location)
    }
} 