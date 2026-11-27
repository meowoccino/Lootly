package com.lootly.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinerLogParserTest {

    private val parser = MinerLogParser()

    @Test
    fun parsesHeartbeatEvent() {
        val line = """{"time":"2026-11-27T18:00:00Z","level":"INFO","msg":"Sent minute watched event","streamer":"shroud"}"""
        val result = parser.parseLine(line, 1L)
        assertNotNull(result.logEntry)
        assertTrue(result.event is ParsedMinerEvent.HeartbeatConfirmed)
        assertEquals("shroud", (result.event as ParsedMinerEvent.HeartbeatConfirmed).channelLogin)
    }

    @Test
    fun parsesEligibleBadgeCampaign() {
        val line = """{"time":"2026-11-27T18:00:00Z","level":"INFO","msg":"Badge campaign eligible","campaign":"Payday 3","badge_name":"Clover","badges":"Clover, Pearl, Joy","game":"PAYDAY 3","required_minutes":30,"ends_at":"2026-12-01T00:00:00Z"}"""
        val result = parser.parseLine(line, 2L)
        assertTrue(result.event is ParsedMinerEvent.BadgeCampaignEligible)
        val ev = result.event as ParsedMinerEvent.BadgeCampaignEligible
        assertEquals("Payday 3", ev.campaignName)
        assertEquals("Clover", ev.badgeName)
        assertEquals(30, ev.requiredMinutes)
    }

    @Test
    fun parsesProgressBarText() {
        val p = parser.parseProgressText("50% [15/30]")
        assertNotNull(p)
        assertEquals(15, p!!.watched)
        assertEquals(30, p.required)
        assertEquals(50, p.percent)
    }
}
