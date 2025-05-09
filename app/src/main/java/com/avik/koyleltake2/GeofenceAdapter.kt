package com.avik.koyleltake2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying geofences in a RecyclerView
 */
class GeofenceAdapter(
    private val geofences: List<CustomGeofencingManager.CustomGeofence>,
    private val onDeleteClick: (CustomGeofencingManager.CustomGeofence) -> Unit
) : RecyclerView.Adapter<GeofenceAdapter.GeofenceViewHolder>() {

    class GeofenceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGeofenceName: TextView = view.findViewById(R.id.tvGeofenceName)
        val tvGeofenceDetails: TextView = view.findViewById(R.id.tvGeofenceDetails)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteGeofence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GeofenceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_geofence, parent, false)
        return GeofenceViewHolder(view)
    }

    override fun getItemCount(): Int = geofences.size

    override fun onBindViewHolder(holder: GeofenceViewHolder, position: Int) {
        val geofence = geofences[position]
        
        // Set name
        holder.tvGeofenceName.text = geofence.name
        
        // Set details (coordinates and radius)
        val details = "${geofence.latitude}, ${geofence.longitude} (${geofence.radius}m)"
        holder.tvGeofenceDetails.text = details
        
        // Set delete button click listener
        holder.btnDelete.setOnClickListener {
            onDeleteClick(geofence)
        }
    }
}