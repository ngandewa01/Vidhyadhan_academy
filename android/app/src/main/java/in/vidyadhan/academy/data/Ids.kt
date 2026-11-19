package `in`.vidyadhan.academy.data

/**
 * Record numbers that never clash between phones: each phone gets its own block of [RANGE] numbers
 * (phone 0 keeps the numbers it already used, phone 1 starts at 1,000,001 and so on).
 */
object Ids {
    const val RANGE = 1_000_000L
    /** This phone's block, given out by the cloud when it signs in (0 until then). */
    @Volatile var device: Long = 0
        set(v) { field = v; synchronized(last) { last.clear() } }
    @Volatile internal var db: AppDb? = null
    private val last = HashMap<String, Long>()

    /** The next free number for [table] in this phone's block (0 = let the database choose, e.g. in tests). */
    fun next(table: String): Long = synchronized(last) {
        val d = db ?: return 0
        val lo = device * RANGE
        val n = (last[table] ?: runCatching {
            d.openHelper.writableDatabase.query("SELECT MAX(id) FROM `$table` WHERE id > ? AND id < ?", arrayOf<Any>(lo, lo + RANGE))
                .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else lo }
        }.getOrDefault(lo)) + 1
        last[table] = n
        n
    }

    fun forget() = synchronized(last) { last.clear() }
}
