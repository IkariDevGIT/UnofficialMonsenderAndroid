package io.github.ikaridevgit.unofficialmonsender.data.settings

import io.github.ikaridevgit.unofficialmonsender.ui.pause.remainingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTest {
    private val start = 1_700_000_000_000L

    @Test
    fun timedPauseEndsOnTime() {
        val pause = Pause.of(30, PauseUnit.MINUTES, start)
        assertTrue(pause.isActive(start + 29 * 60_000L))
        assertFalse(pause.isActive(start + 30 * 60_000L))
        assertEquals(0.5f, pause.progress(start + 15 * 60_000L), 0.001f)
    }

    @Test
    fun indefinitePauseNeverEnds() {
        val pause = Pause.indefinite(start)
        assertTrue(pause.isActive(Long.MAX_VALUE - 1))
        assertEquals(0f, pause.progress(start + 1_000_000L))
    }

    @Test
    fun monthsFollowTheCalendar() {
        val pause = Pause.of(1, PauseUnit.MONTHS, start)
        val days = (pause.until - start) / PauseUnit.DAYS.millis
        assertTrue(days in 28..31)
    }

    @Test
    fun expiredPauseIsNotActive() {
        val settings = AppSettings(pause = Pause.of(10, PauseUnit.SECONDS, start))
        assertNull(settings.activePause(start + 10_000L))
        assertEquals(settings.pause, settings.activePause(start + 9_999L))
    }

    @Test
    fun remainingReadsAsAClock() {
        assertEquals("4:09", remainingText(249_000L))
        assertEquals("3h 05m", remainingText((3 * 3600 + 5 * 60) * 1000L))
        assertEquals("2d 7h", remainingText((2 * 86400 + 7 * 3600) * 1000L))
    }
}
