package com.avik.koyleltake2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class LogEntryAdapter(
    private val logEntries: MutableList<LogEntry>,
    private val hebrewTimestamps: List<String>,
    private val onDeleteClick: (LogEntry) -> Unit,
    private val textColor: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == logEntries.size) TYPE_FOOTER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timestamp, parent, false)
            LogEntryViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_footer, parent, false)
            FooterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is LogEntryViewHolder && position < logEntries.size) {
            holder.bind(logEntries[position], hebrewTimestamps[position], textColor)
        }
    }

    override fun getItemCount(): Int = logEntries.size + 1 // +1 for footer

    inner class LogEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvDayOfWeek: TextView = itemView.findViewById(R.id.tvDayOfWeek)
        private val tvSeder: TextView = itemView.findViewById(R.id.tvSeder)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val ivCar: ImageButton? = itemView.findViewById(R.id.ivCar)

        fun bind(logEntry: LogEntry, hebrewTimestamp: String, textColor: Int) {
            val date = java.util.Date(logEntry.id)
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val dayOfWeekFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
            val hour = java.util.Calendar.getInstance().apply { time = date }.get(java.util.Calendar.HOUR_OF_DAY)

            val time = timeFormat.format(date)
            // Remove the time from the end of the hebrewTimestamp for the date
            val dateOnly = hebrewTimestamp.substringBeforeLast(' ')

            tvTime.text = time
            tvDate.text = dateOnly
            tvDayOfWeek.text = dayOfWeekFormat.format(date)

            // Apply text color
            tvTime.setTextColor(textColor)
            tvDate.setTextColor(textColor)
            tvDayOfWeek.setTextColor(textColor)
            tvSeder.setTextColor(textColor)

            // Determine seder based on hour and entry type
            val seder = if (logEntry.type == LogEntryType.CAR) {
                "" // Don't show seder for car entries
            } else {
                when (hour) {
                    in 9..12 -> "סדר א'"
                    in 15..18 -> "סדר ב'"
                    else -> ""
                }
            }
            tvSeder.text = seder

            if (logEntry.type == LogEntryType.CAR) {
                ivCar?.visibility = View.VISIBLE
            } else {
                ivCar?.visibility = View.GONE
            }

            btnDelete.setOnClickListener { onDeleteClick(logEntry) }
        }
    }

    inner class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
} 