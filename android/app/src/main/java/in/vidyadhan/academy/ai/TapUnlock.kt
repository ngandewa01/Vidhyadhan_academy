package `in`.vidyadhan.academy.ai

/**
 * Quick taps on the key row unlock editing, like tapping "Build number" to enable developer options.
 * The 10th tap unlocks. Taps more than [gapMs] apart start again. The count is shown only from the 7th tap.
 */
class TapUnlock(private val need: Int = 10, private val gapMs: Long = 2000, private val now: () -> Long = { System.currentTimeMillis() }) {
    private var count = 0
    private var last = 0L

    /** Returns true when this tap unlocks. */
    fun tap(): Boolean {
        val t = now()
        count = if (count == 0 || t - last > gapMs) 1 else count + 1
        last = t
        return count >= need
    }

    /** "3 more", from the 7th tap until unlocked; null before that. */
    fun hint(): String? = if (count in 7 until need) "${need - count} more" else null

    fun reset() { count = 0 }
}
