package com.avik.koyleltake2

import java.io.Serializable

enum class LogEntryType { NORMAL, CAR, GEOFENCE }

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val type: LogEntryType = LogEntryType.NORMAL,
    val amount: Int? = null,
    val locationName: String? = null
) : Serializable