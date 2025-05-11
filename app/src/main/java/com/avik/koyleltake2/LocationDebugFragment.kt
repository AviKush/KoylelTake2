package com.avik.koyleltake2

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import java.util.*

/**
 * Fragment that displays location debug information
 */
class LocationDebugFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var clearLogButton: Button
    private lateinit var locationCountTextView: TextView
    private lateinit var showGeofencesButton: Button
    private lateinit var forceCreateEntryButton: Button
    private lateinit var adapter: LocationDebugAdapter
    private lateinit var locationDebugManager: LocationDebugManager
    private lateinit var customGeofencingManager: CustomGeofencingManager
    
    // Handler to periodically refresh the location data
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshLocationData()
            handler.postDelayed(this, 10000) // Refresh every 10 seconds
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_location_debug, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        recyclerView = view.findViewById(R.id.locationRecyclerView)
        clearLogButton = view.findViewById(R.id.clearLogButton)
        locationCountTextView = view.findViewById(R.id.locationCountTextView)
        showGeofencesButton = view.findViewById(R.id.showGeofencesButton)
        forceCreateEntryButton = view.findViewById(R.id.forceCreateEntryButton)
        
        // Get managers
        locationDebugManager = LocationDebugManager.getInstance(requireContext())
        customGeofencingManager = CustomGeofencingManager(requireContext())
        
        // Set up the RecyclerView
        adapter = LocationDebugAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // Set up the clear button
        clearLogButton.setOnClickListener {
            locationDebugManager.clearLocationHistory()
            refreshLocationData()
        }
        
        // Set up the show geofences button
        showGeofencesButton.setOnClickListener {
            showGeofenceDebugInfo()
        }
        
        // Set up the force create entry button
        forceCreateEntryButton.setOnClickListener {
            forceCreateGeofenceEntry()
        }
        
        // Load initial data
        refreshLocationData()
    }
    
    override fun onResume() {
        super.onResume()
        // Start auto-refresh
        handler.post(refreshRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // Stop auto-refresh
        handler.removeCallbacks(refreshRunnable)
    }
    
    /**
     * Refresh the location data from the debug manager
     */
    private fun refreshLocationData() {
        val locationHistory = locationDebugManager.getLocationHistory()
        adapter.setData(locationHistory)
        locationCountTextView.text = getString(R.string.logged_locations, locationHistory.size)
    }
    
    /**
     * Show debug info about geofences
     */
    private fun showGeofenceDebugInfo() {
        val geofences = customGeofencingManager.getGeofences()
        val builder = StringBuilder()
        
        if (geofences.isEmpty()) {
            builder.append("No geofences configured\n\n")
            
            // Try to read raw SharedPreferences data
            try {
                val prefs = requireContext().getSharedPreferences("geofence_prefs", android.content.Context.MODE_PRIVATE)
                val rawData = prefs.getString("saved_geofences", "null")
                builder.append("Raw SharedPreferences data:\n$rawData")
            } catch (e: Exception) {
                builder.append("Error reading SharedPreferences: ${e.message}")
            }
        } else {
            builder.append("Configured Geofences (${geofences.size}):\n\n")
            geofences.forEachIndexed { index, geofence ->
                builder.append("${index + 1}. ${geofence.name}\n")
                builder.append("   ID: ${geofence.id}\n")
                builder.append("   Lat: ${geofence.latitude}, Lng: ${geofence.longitude}\n")
                builder.append("   Radius: ${geofence.radius}m\n\n")
            }
        }
        
        // Show dialog with geofence info
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Geofence Information")
            .setMessage(builder.toString())
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Force create a geofence entry for the nearest geofence
     */
    private fun forceCreateGeofenceEntry() {
        val geofences = customGeofencingManager.getGeofences()
        
        if (geofences.isEmpty()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("No Geofences")
                .setMessage("No geofences are configured. Please add a geofence first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Get the current location
        val locationHistory = locationDebugManager.getLocationHistory()
        if (locationHistory.isEmpty()) {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("No Location")
                .setMessage("No location data available. Please wait for a location update.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Use the latest location
        val latestLocation = locationHistory.last()
        
        // Create a Location object
        val location = android.location.Location("debug").apply {
            latitude = latestLocation.latitude
            longitude = latestLocation.longitude
            accuracy = latestLocation.accuracy
        }
        
        // Find the nearest geofence
        var nearestGeofence: CustomGeofencingManager.CustomGeofence? = null
        var shortestDistance = Float.MAX_VALUE
        
        for (geofence in geofences) {
            val distance = getDistanceTo(location, geofence.latitude, geofence.longitude)
            if (distance < shortestDistance) {
                shortestDistance = distance
                nearestGeofence = geofence
            }
        }
        
        if (nearestGeofence == null) {
            // This shouldn't happen if we have geofences
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setMessage("Could not find nearest geofence")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Create a timestamp
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
            "${nearestGeofence.name} $seder"
        } else {
            nearestGeofence.name
        }
        
        // Create log entry
        val logEntry = LogEntry(
            id = currentTime,
            timestamp = formatTimestamp(currentTime),
            type = LogEntryType.GEOFENCE,
            locationName = formattedLocationName
        )
        
        // Save the entry
        saveLogEntry(logEntry)
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Entry Created")
            .setMessage("Created entry for ${nearestGeofence.name} at distance ${shortestDistance.toInt()}m")
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Calculate distance between two points
     */
    private fun getDistanceTo(location: android.location.Location, latitude: Double, longitude: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            location.latitude, location.longitude,
            latitude, longitude,
            results
        )
        return results[0]
    }
    
    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
    
    /**
     * Save a log entry to shared preferences
     */
    private fun saveLogEntry(logEntry: LogEntry) {
        val sharedPreferences = requireContext().getSharedPreferences("log_entries", Context.MODE_PRIVATE)
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
    
    /**
     * Adapter for displaying location entries
     */
    private inner class LocationDebugAdapter : 
        RecyclerView.Adapter<LocationDebugAdapter.LocationViewHolder>() {
        
        private val locationEntries = mutableListOf<LocationDebugManager.LocationEntry>()
        private val coordFormat = DecimalFormat("0.000000")
        
        fun setData(newData: List<LocationDebugManager.LocationEntry>) {
            locationEntries.clear()
            locationEntries.addAll(newData)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_location_debug, parent, false)
            return LocationViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
            // Display entries in reverse order (newest first)
            val entry = locationEntries[locationEntries.size - 1 - position]
            holder.bind(entry)
        }
        
        override fun getItemCount(): Int = locationEntries.size
        
        inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
            private val coordinatesTextView: TextView = itemView.findViewById(R.id.coordinatesTextView)
            private val accuracyTextView: TextView = itemView.findViewById(R.id.accuracyTextView)
            private val geofenceStatusTextView: TextView = itemView.findViewById(R.id.geofenceStatusTextView)
            
            fun bind(entry: LocationDebugManager.LocationEntry) {
                timestampTextView.text = entry.getFormattedTime()
                coordinatesTextView.text = "Lat: ${coordFormat.format(entry.latitude)}, " +
                        "Lng: ${coordFormat.format(entry.longitude)}"
                accuracyTextView.text = "Accuracy: ${entry.accuracy.toInt()}m (${entry.provider})"
                geofenceStatusTextView.text = entry.geofenceStatus
            }
        }
    }
    
    companion object {
        fun newInstance() = LocationDebugFragment()
    }
} 