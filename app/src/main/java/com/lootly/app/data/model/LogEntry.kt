package com.lootly.app.data.model

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val category: String,
    val message: String,
    val rawLine: String
)
