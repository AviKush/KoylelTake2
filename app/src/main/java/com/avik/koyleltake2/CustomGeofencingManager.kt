package com.avik.koyleltake2

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            
            // Save to persistent storage
            saveGeofences()
            
            // Start location monitoring service if not already running
            startLocationService()
            
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding geofence", e)
            onError(e)
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
     * Check if a location is inside any geofence and trigger transitions
     */
    fun processLocation(location: Location) {
        for (geofence in geofences) {
            val distance = getDistanceTo(location, geofence.latitude, geofence.longitude)
            val isInside = distance <= geofence.radius
            
            val state = geofenceStates[geofence.id] ?: GeofenceState(geofence.id)
            
            // Check for transitions (only trigger if state changed and enough time passed)
            val now = System.currentTimeMillis()
            val timeThreshold = 60 * 1000 // 1 minute to avoid multiple triggers
            
            if (isInside != state.isInside && (now - state.lastTransitionTime) > timeThreshold) {
                // State changed - trigger event
                if (isInside) {
                    // Entered geofence
                    triggerGeofenceTransition(geofence, GEOFENCE_TRANSITION_ENTER)
                } else {
                    // Exited geofence
                    triggerGeofenceTransition(geofence, GEOFENCE_TRANSITION_EXIT)
                }
                
                // Update state
                state.isInside = isInside
                state.lastTransitionTime = now
                geofenceStates[geofence.id] = state
            }
        }
    }
    
    /**
     * Trigger a geofence transition broadcast
     */
    private fun triggerGeofenceTransition(geofence: CustomGeofence, transitionType: Int) {
        Log.d(TAG, "Geofence transition: ${geofence.name}, type: $transitionType")
        
        // Send broadcast
        val intent = Intent(ACTION_GEOFENCE_TRANSITION).apply {
            putExtra(EXTRA_GEOFENCE_ID, geofence.id)
            putExtra(EXTRA_GEOFENCE_NAME, geofence.name)
            putExtra(EXTRA_TRANSITION_TYPE, transitionType)
        }
        context.sendBroadcast(intent)
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
     * Save geofences to SharedPreferences
     */
    private fun saveGeofences() {
        try {
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
            
            sharedPreferences.edit()
                .putString(GEOFENCES_KEY, jsonArray.toString())
                .apply()
                
        } catch (e: JSONException) {
            Log.e(TAG, "Error saving geofences", e)
        }
    }
    
    /**
     * Load geofences from SharedPreferences
     */
    private fun loadGeofences() {
        try {
            val jsonString = sharedPreferences.getString(GEOFENCES_KEY, null) ?: return
            val jsonArray = JSONArray(jsonString)
            
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
            }
            
            // Start location service if we have geofences
            if (geofences.isNotEmpty()) {
                startLocationService()
            }
            
        } catch (e: JSONException) {
            Log.e(TAG, "Error loading geofences", e)
        }
    }
    
    /**
     * Start the location monitoring service
     */
    private fun startLocationService() {
        val serviceIntent = Intent(context, LocationMonitoringService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
    
    /**
     * Stop the location monitoring service
     */
    private fun stopLocationService() {
        val serviceIntent = Intent(context, LocationMonitoringService::class.java)
        context.stopService(serviceIntent)
    }
} 