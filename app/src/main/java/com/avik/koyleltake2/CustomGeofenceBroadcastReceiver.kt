package com.avik.koyleltake2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import java.util.*

/**
 * Broadcast receiver that handles geofence transition events.
 */
class CustomGeofenceBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast with action: ${intent.action}")
        
        if (intent.action == CustomGeofencingManager.ACTION_GEOFENCE_TRANSITION) {
            val geofenceId = intent.getStringExtra(CustomGeofencingManager.EXTRA_GEOFENCE_ID) ?: return
            val geofenceName = intent.getStringExtra(CustomGeofencingManager.EXTRA_GEOFENCE_NAME) ?: return
            val transitionType = intent.getIntExtra(CustomGeofencingManager.EXTRA_TRANSITION_TYPE, -1)
            
            if (transitionType == -1) return
            
            Log.d(TAG, "Geofence transition: $geofenceName, transition: $transitionType")
            
            when (transitionType) {
                CustomGeofencingManager.GEOFENCE_TRANSITION_ENTER -> {
                    // User entered geofence - log timestamp
                    Log.d(TAG, "ENTER: About to log geofence entry for: $geofenceName")
                    logGeofenceEntry(context, geofenceName)
                }
                CustomGeofencingManager.GEOFENCE_TRANSITION_EXIT -> {
                    // User exited geofence - we can handle this case if needed
                    Log.d(TAG, "Exited geofence: $geofenceName")
                }
            }
        } else {
            Log.d(TAG, "Received unhandled broadcast: ${intent.action}")
        }
    }
    
    /**
     * Log a geofence entry to our app's log system
     */
    private fun logGeofenceEntry(context: Context, locationName: String) {
        try {
            Log.d(TAG, "Starting to log geofence entry for: $locationName")
            
            // Get the app instance to use its formatTimestamp method
            val app = context.applicationContext as MyApplication
            
            // Create timestamp
            val currentTime = System.currentTimeMillis()
            val formattedTime = app.formatTimestamp(currentTime)
            Log.d(TAG, "Formatted timestamp: $formattedTime")
            
            // Determine seder based on hour
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = currentTime
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val seder = when (hour) {
                in 9..12 -> "'סדר א"
                in 15..18 -> "'סדר ב"
                else -> ""
            }
            Log.d(TAG, "Current hour: $hour, Seder: $seder")
            
            // Format the location name with seder if applicable
            val formattedLocationName = if (seder.isNotEmpty()) {
                "$locationName $seder"
            } else {
                locationName
            }
            Log.d(TAG, "Formatted location name: $formattedLocationName")
            
            // Create log entry
            val logEntry = LogEntry(
                id = currentTime,
                timestamp = formattedTime,
                type = LogEntryType.GEOFENCE,
                locationName = formattedLocationName
            )
            
            // Save the log entry
            saveLogEntry(context, logEntry)
            Log.d(TAG, "Saved log entry to SharedPreferences")
            
            // Show a toast notification
            Toast.makeText(context, 
                context.getString(R.string.geofence_entered, locationName), 
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging geofence entry", e)
        }
    }
    
    /**
     * Save the log entry to shared preferences
     */
    private fun saveLogEntry(context: Context, logEntry: LogEntry) {
        // Get existing entries
        val sharedPreferences = context.getSharedPreferences("log_entries", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("log_entries_json", null)
        val jsonArray = if (jsonString != null) {
            org.json.JSONArray(jsonString)
        } else {
            org.json.JSONArray()
        }
        
        // Add new entry
        val obj = org.json.JSONObject()
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
} 