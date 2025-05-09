package com.avik.koyleltake2

enum class LogEntryType { NORMAL, CAR }

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val type: LogEntryType = LogEntryType.NORMAL,
    val amount: Int? = null
)