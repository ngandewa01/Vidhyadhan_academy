package `in`.vidyadhan.academy

import `in`.vidyadhan.academy.ai.TapUnlock
import org.junit.Assert.*
import org.junit.Test

/** The 10-tap key lock. */
class TapUnlockTest {
    private var t = 0L
    private fun taps(n: Int, gap: Long = 300): TapUnlock {
        val u = TapUnlock(now = { t })
        repeat(n) { t += gap; u.tap() }
        return u
    }

    @Test fun tenQuickTapsUnlock() {
        var t2 = 0L
        val u = TapUnlock(now = { t2 })
        val results = (1..10).map { t2 += 300; u.tap() }
        assertFalse(results.dropLast(1).any { it })
        assertTrue(results.last())
    }

    @Test fun pauseStartsOver() {
        var now = 0L
        val u = TapUnlock(now = { now })
        repeat(8) { now += 300; u.tap() }
        now += 5000 // a long pause
        val unlocked = (1..9).map { now += 300; u.tap() }.any { it }
        assertFalse(unlocked)
    }

    @Test fun hintOnlyFromSeventh() {
        var now = 0L
        val u = TapUnlock(now = { now })
        repeat(6) { now += 300; u.tap() }
        assertNull(u.hint())
        now += 300; u.tap()
        assertEquals("3 more", u.hint())
    }
}
