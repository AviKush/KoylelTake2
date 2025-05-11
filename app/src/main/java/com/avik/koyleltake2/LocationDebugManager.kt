package com.avik.koyleltake2

import android.content.Context
import android.location.Location
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Class that manages debug information for location updates.
 * Stores location history with timestamps for debugging purposes.
 */
class LocationDebugManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationDebugManager"
        private const val PREFS_NAME = "location_debug_prefs"
        private const val LOCATION_HISTORY_KEY = "location_history"
        private const val MAX_ENTRIES = 100 // Maximum number of location entries to store
        
        // Singleton instance
        @Volatile
        private var instance: LocationDebugManager? = null
        
        fun getInstance(context: Context): LocationDebugManager {
            return instance ?: synchronized(this) {
                instance ?: LocationDebugManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Data class to represent a location entry
    data class LocationEntry(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val provider: String,
        val timestamp: Long,
        val geofenceStatus: String // Information about geofence status
    ) {
        fun getFormattedTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
    
    // List of location entries
    private val locationHistory = mutableListOf<LocationEntry>()
    
    init {
        // Load saved location history
        loadLocationHistory()
    }
    
    /**
     * Add a new location entry to the history
     */
    fun addLocationEntry(location: Location, geofenceStatus: String) {
        val entry = LocationEntry(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            provider = location.provider ?: "unknown",
            timestamp = System.currentTimeMillis(),
            geofenceStatus = geofenceStatus
        )
        
        // Add to in-memory list
        synchronized(locationHistory) {
            locationHistory.add(entry)
            
            // Trim the list if it gets too large
            if (locationHistory.size > MAX_ENTRIES) {
                locationHistory.removeAt(0)
            }
        }
        
        // Save to persistent storage
        saveLocationHistory()
        
        Log.d(TAG, "Added location entry: ${entry.latitude}, ${entry.longitude}, ${entry.getFormattedTime()}")
    }
    
    /**
     * Get all location entries
     */
    fun getLocationHistory(): List<LocationEntry> {
        synchronized(locationHistory) {
            return locationHistory.toList()
        }
    }
    
    /**
     * Clear all location entries
     */
    fun clearLocationHistory() {
        synchronized(locationHistory) {
            locationHistory.clear()
        }
        saveLocationHistory()
    }
    
    /**
     * Save location history to SharedPreferences
     */
    private fun saveLocationHistory() {
        try {
            val jsonArray = JSONArray()
            synchronized(locationHistory) {
                for (entry in locationHistory) {
                    val jsonObject = JSONObject().apply {
                        put("latitude", entry.latitude)
                        put("longitude", entry.longitude)
                        put("accuracy", entry.accuracy)
                        put("provider", entry.provider)
                        put("timestamp", entry.timestamp)
                        put("geofenceStatus", entry.geofenceStatus)
                    }
                    jsonArray.put(jsonObject)
                }
            }
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(LOCATION_HISTORY_KEY, jsonArray.toString())
                .apply()
                
        } catch (e: JSONException) {
            Log.e(TAG, "Error saving location history", e)
        }
    }
    
    /**
     * Load location history from SharedPreferences
     */
    private fun loadLocationHistory() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(LOCATION_HISTORY_KEY, null) ?: return
            val jsonArray = JSONArray(jsonString)
            
            // Clear existing history
            synchronized(locationHistory) {
                locationHistory.clear()
                
                // Load saved entries
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val entry = LocationEntry(
                        latitude = jsonObject.getDouble("latitude"),
                        longitude = jsonObject.getDouble("longitude"),
                        accuracy = jsonObject.getDouble("accuracy").toFloat(),
                        provider = jsonObject.getString("provider"),
                        timestamp = jsonObject.getLong("timestamp"),
                        geofenceStatus = jsonObject.getString("geofenceStatus")
                    )
                    locationHistory.add(entry)
                }
            }
            
        } catch (e: JSONException) {
            Log.e(TAG, "Error loading location history", e)
        }
    }
} 