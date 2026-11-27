package com.lootly.app.engine

import com.lootly.app.data.model.LogEntry
import com.lootly.app.data.model.LogLevel
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CircularLogBuffer(
    private val maxEntries: Int = 1000
) {
    private val idCounter = AtomicLong(1L)
    private val buffer = ArrayDeque<LogEntry>(maxEntries)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun append(entry: LogEntry) {
        if (buffer.size >= maxEntries) {
            buffer.removeFirst()
        }
        val assigned = if (entry.id <= 0L) entry.copy(id = idCounter.getAndIncrement()) else entry
        buffer.addLast(assigned)
        _entries.value = buffer.toList()
    }

    fun appendFormatted(
        level: LogLevel,
        category: String,
        message: String,
        rawLine: String = message
    ) {
        append(
            LogEntry(
                id = idCounter.getAndIncrement(),
                timestamp = currentTimeShort(),
                level = level,
                category = category,
                message = message,
                rawLine = rawLine
            )
        )
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    companion object {
        fun currentTimeShort(): String {
            val now = LocalTime.now()
            return String.format(Locale.US, "%02d:%02d:%02d", now.hour, now.minute, now.second)
        }
    }
}
