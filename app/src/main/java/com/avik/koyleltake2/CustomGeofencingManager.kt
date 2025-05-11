package com.avik.koyleltake2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * A custom geofencing implementation that doesn't rely on Google Play Services.
 */
class CustomGeofencingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CustomGeofencingManager"
        private const val PREFS_NAME = "geofence_prefs"
        private const val GEOFENCES_KEY = "saved_geofences"
        
        // Broadcast action for location updates
        const val ACTION_GEOFENCE_TRANSITION = "com.avik.koyleltake2.ACTION_GEOFENCE_TRANSITION"
        const val EXTRA_GEOFENCE_ID = "geofence_id"
        const val EXTRA_GEOFENCE_NAME = "geofence_name"
        const val EXTRA_TRANSITION_TYPE = "transition_type"
        
        // Transition types
        const val GEOFENCE_TRANSITION_ENTER = 1
        const val GEOFENCE_TRANSITION_EXIT = 2
        
        // Default geofence radius in meters
        const val DEFAULT_RADIUS = 100f
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        // Use application context to prevent memory leaks
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Data class to represent a geofence
    data class CustomGeofence(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radius: Float = DEFAULT_RADIUS
    )
    
    // Class to track geofence state
    data class GeofenceState(
        val id: String,
        var isInside: Boolean = false,
        var lastTransitionTime: Long = 0
    )
    
    // Keep track of created geofences
    private val geofences = mutableListOf<CustomGeofence>()
    
    // Keep track of geofence states to avoid duplicate events
    private val geofenceStates = mutableMapOf<String, GeofenceState>()
    
    init {
        Log.d(TAG, "Initializing CustomGeofencingManager with context: ${context.javaClass.simpleName}")
        
        // Load existing geofences
        loadGeofences()
    }
    
    /**
     * Add a new geofence
     */
    fun addGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radius: Float = DEFAULT_RADIUS,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val id = UUID.randomUUID().toString()
            val geofence = CustomGeofence(id, name, latitude, longitude, radius)
            
            // Add to in-memory list
            geofences.add(geofence)
            geofenceStates[id] = GeofenceState(id)
            
            // Log the geofence details
            Log.d(TAG, "Adding new geofence: '${name}' at ${latitude}, ${longitude} with radius ${radius}m")
            
            // Save to persistent storage
            saveGeofences()
            
            // Verify the geofence was saved by reloading
            val savedCount = getSavedGeofenceCount()
            Log.d(TAG, "Geofence added, verified count: $savedCount")
            
            // Start location monitoring service if not already running
            if (geofences.isNotEmpty()) {
                Log.d(TAG, "Starting location service after adding geofence")
                startLocationService()
            }
            
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding geofence", e)
            onError(e)
        }
    }
    
    /**
     * Get the number of saved geofences from preferences
     */
    private fun getSavedGeofenceCount(): Int {
        try {
            val jsonString = sharedPreferences.getString(GEOFENCES_KEY, null) ?: return 0
            val jsonArray = JSONArray(jsonString)
            return jsonArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting saved geofence count", e)
            return -1
        }
    }
    
    /**
     * Remove a geofence by ID
     */
    fun removeGeofence(
        id: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // Remove from in-memory list
            geofences.removeAll { it.id == id }
            geofenceStates.remove(id)
            
            // Save changes
            saveGeofences()
            
            // If no more geofences, stop the service
            if (geofences.isEmpty()) {
                stopLocationService()
            }
            
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing geofence", e)
            onError(e)
        }
    }
    
    /**
     * Get all geofences
     */
    fun getGeofences(): List<CustomGeofence> {
        return geofences.toList()
    }
    
    /**
     * Calculate distance between two points
     */
    fun getDistanceTo(location: Location, latitude: Double, longitude: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            latitude, longitude,
            results
        )
        return results[0]
    }
    
    /**
     * Check if a location is inside a specific geofence
     */
    fun isLocationInsideGeofence(location: Location, geofence: CustomGeofence): Boolean {
        val distance = getDistanceTo(location, geofence.latitude, geofence.longitude)
        return distance <= geofence.radius
    }
    
    /**
     * Save geofences to SharedPreferences
     */
    private fun saveGeofences() {
        try {
            Log.d(TAG, "Saving ${geofences.size} geofences to SharedPreferences")
            
            val jsonArray = JSONArray()
            for (geofence in geofences) {
                val jsonObject = JSONObject().apply {
                    put("id", geofence.id)
                    put("name", geofence.name)
                    put("latitude", geofence.latitude)
                    put("longitude", geofence.longitude)
                    put("radius", geofence.radius)
                }
                jsonArray.put(jsonObject)
            }
            
            // Commit changes immediately instead of applying asynchronously
            val result = sharedPreferences.edit()
                .putString(GEOFENCES_KEY, jsonArray.toString())
                .commit()
                
            if (result) {
                Log.d(TAG, "Successfully saved geofences to SharedPreferences")
            } else {
                Log.e(TAG, "Failed to save geofences to SharedPreferences")
            }
                
        } catch (e: JSONException) {
            Log.e(TAG, "Error saving geofences", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving geofences", e)
        }
    }
    
    /**
     * Load geofences from SharedPreferences
     */
    private fun loadGeofences() {
        try {
            val jsonString = sharedPreferences.getString(GEOFENCES_KEY, null)
            if (jsonString == null) {
                Log.d(TAG, "No saved geofences found in SharedPreferences")
                return
            }
            
            val jsonArray = JSONArray(jsonString)
            Log.d(TAG, "Loading ${jsonArray.length()} saved geofences from SharedPreferences")
            
            // Clear existing geofences
            geofences.clear()
            geofenceStates.clear()
            
            // Load saved geofences
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getString("id")
                val name = jsonObject.getString("name")
                val latitude = jsonObject.getDouble("latitude")
                val longitude = jsonObject.getDouble("longitude")
                val radius = jsonObject.getDouble("radius").toFloat()
                
                val geofence = CustomGeofence(id, name, latitude, longitude, radius)
                geofences.add(geofence)
                geofenceStates[id] = GeofenceState(id)
                
                Log.d(TAG, "Loaded geofence: '${name}' at ${latitude}, ${longitude} with radius ${radius}m")
            }
            
            // Start location service if we have geofences
            if (geofences.isNotEmpty()) {
                Log.d(TAG, "Starting location service for ${geofences.size} loaded geofences")
                startLocationService()
                
                // Check if already inside any geofence at startup
                checkInitialGeofenceStates()
            }
            
        } catch (e: JSONException) {
            Log.e(TAG, "Error loading geofences", e)
        }
    }
    
    /**
     * Check if the device is already inside any geofences at startup
     */
    private fun checkInitialGeofenceStates() {
        try {
            // Only check initial states once per app session using a flag
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val alreadyChecked = prefs.getBoolean("initial_state_checked", false)
            
            if (alreadyChecked) {
                Log.d(TAG, "Initial geofence state already checked this session, skipping")
                return
            }
            
            // Set the flag to indicate we've checked
            prefs.edit().putBoolean("initial_state_checked", true).apply()
            
            // Get the last known location
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                
                lastKnownLocation?.let { location ->
                    // Check if inside any geofence
                    for (geofence in geofences) {
                        val distance = getDistanceTo(location, geofence.latitude, geofence.longitude)
                        val isInside = distance <= geofence.radius
                        
                        // If already inside, set initial state and trigger an entry event
                        if (isInside) {
                            Log.d(TAG, "Already inside geofence: ${geofence.name} at startup")
                            val state = geofenceStates[geofence.id] ?: GeofenceState(geofence.id)
                            state.isInside = true
                            state.lastTransitionTime = System.currentTimeMillis() - 70000 // Set time in the past to allow immediate trigger
                            geofenceStates[geofence.id] = state
                            
                            // Create a broadcast intent for the ENTER transition
                            val intent = Intent(ACTION_GEOFENCE_TRANSITION).apply {
                                putExtra(EXTRA_GEOFENCE_ID, geofence.id)
                                putExtra(EXTRA_GEOFENCE_NAME, geofence.name)
                                putExtra(EXTRA_TRANSITION_TYPE, GEOFENCE_TRANSITION_ENTER)
                            }
                            context.sendBroadcast(intent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking initial geofence states", e)
        }
    }
    
    /**
     * Get geofence status information for a location (used for debugging)
     */
    fun getGeofenceStatus(location: Location): String {
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
     * Start the location monitoring service
     */
    private fun startLocationService() {
        try {
            // Check if service is already running by querying the system
            val isServiceRunning = isServiceRunning(LocationMonitoringService::class.java)
            
            if (isServiceRunning) {
                Log.d(TAG, "LocationMonitoringService is already running, skipping start")
                return
            }
            
            Log.d(TAG, "Starting location monitoring service")
            val serviceIntent = Intent(context, LocationMonitoringService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d(TAG, "Started service with startForegroundService")
            } else {
                context.startService(serviceIntent)
                Log.d(TAG, "Started service with startService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service", e)
        }
    }
    
    /**
     * Check if a service is running
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            
            for (service in runningServices) {
                if (serviceClass.name == service.service.className) {
                    Log.d(TAG, "Found running service: ${serviceClass.name}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if service is running", e)
        }
        
        return false
    }
    
    /**
     * Stop the location monitoring service
     */
    private fun stopLocationService() {
        try {
            Log.d(TAG, "Stopping location monitoring service")
            val serviceIntent = Intent(context, LocationMonitoringService::class.java)
            context.stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location service", e)
        }
    }
} 