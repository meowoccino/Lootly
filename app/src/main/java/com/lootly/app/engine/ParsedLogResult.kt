package com.lootly.app.engine

import com.lootly.app.data.model.LogEntry

data class ParsedLogResult(
    val logEntry: LogEntry?,
    val event: ParsedMinerEvent
)
