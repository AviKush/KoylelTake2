package com.avik.koyleltake2

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

/**
 * Fragment that displays location debug information
 */
class LocationDebugFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var clearLogButton: Button
    private lateinit var locationCountTextView: TextView
    private lateinit var adapter: LocationDebugAdapter
    private lateinit var locationDebugManager: LocationDebugManager
    
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
        
        // Get the debug manager
        locationDebugManager = LocationDebugManager.getInstance(requireContext())
        
        // Set up the RecyclerView
        adapter = LocationDebugAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // Set up the clear button
        clearLogButton.setOnClickListener {
            locationDebugManager.clearLocationHistory()
            refreshLocationData()
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