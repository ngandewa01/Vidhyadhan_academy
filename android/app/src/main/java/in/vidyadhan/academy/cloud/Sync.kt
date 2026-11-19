package `in`.vidyadhan.academy.cloud

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.room.InvalidationTracker
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Keeps this phone's database and the cloud (Firestore) the same.
 *
 * The phone's own database stays the main copy, so everything works offline. Every table is mirrored to
 * `academies/{academy}/{Table}/{id}`. After any change here the changed rows are sent up; changes made on
 * another phone come down live. A small side database remembers what each row looked like when it was last
 * in step with the cloud, so only real changes travel (and deleted rows are deleted on the other side too).
 */
object Sync {
    /** Parents before children, so pulled rows can be linked. */
    val TABLES = listOf("Student", "Enquiry", "SeatType", "LibraryRoom", "MemberPlan", "LibraryMember", "Installment", "StudentPayment", "LibraryPayment", "Note", "Expense")
    private const val TAG = "Sync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var listeners = mutableListOf<ListenerRegistration>()
    private var observer: InvalidationTracker.Observer? = null
    private var root: DocumentReference? = null
    private var db: AppDb? = null
    private var known: Known? = null
    /** Pulled rows that could not be saved yet (their parent had not arrived). */
    private val waiting = mutableListOf<Pair<String, DocumentSnapshot>>()
    private var pulling = false

    val running get() = job != null

    fun start(ctx: Context, academy: String) {
        stop()
        val app = ctx.applicationContext
        val d = AppDb.get(app).also { db = it }
        known = Known(app)
        val r = FirebaseFirestore.getInstance().collection("academies").document(academy).also { root = it }
        observer = object : InvalidationTracker.Observer(TABLES.toTypedArray()) {
            override fun onInvalidated(tables: Set<String>) { if (!pulling) signal.trySend(Unit) }
        }.also { d.invalidationTracker.addObserver(it) }
        AppSettings.onChange = { k -> if (AppSettings.isShared(k)) scope.launch { pushSettings() } }
        job = scope.launch {
            signal.trySend(Unit)
            for (x in signal) {
                delay(1200) // gather quick changes together
                runCatching { lock.withLock { push() } }.onFailure { Cloud.failed(it) }
            }
        }
        TABLES.forEach { t ->
            listeners += r.collection(t).addSnapshotListener { snap, err ->
                if (err != null) { Cloud.failed(err); return@addSnapshotListener }
                if (snap != null) scope.launch { runCatching { lock.withLock { pull(t, snap) } }.onFailure { Cloud.failed(it) } }
            }
        }
        listeners += r.collection("meta").document("settings").addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists() && !snap.metadata.hasPendingWrites()) {
                val m = snap.data.orEmpty().mapValues { it.value?.toString().orEmpty() }
                scope.launch(Dispatchers.Main) { AppSettings.applyShared(m) }
            }
        }
    }

    fun stop() {
        listeners.forEach { it.remove() }; listeners.clear()
        observer?.let { db?.invalidationTracker?.removeObserver(it) }; observer = null
        job?.cancel(); job = null
        AppSettings.onChange = null
        synchronized(waiting) { waiting.clear() }
    }

    fun syncNow() { signal.trySend(Unit) }

    // ───────────── this phone → cloud ─────────────

    private fun push() {
        val d = db ?: return; val r = root ?: return; val k = known ?: return
        var batch = r.firestore.batch(); var n = 0; var sent = 0
        fun flush() { if (n > 0) { batch.commit().addOnFailureListener { Cloud.failed(it) }; batch = r.firestore.batch(); n = 0 } }
        for (t in TABLES) {
            val local = rows(d, t)
            val before = k.all(t)
            for ((id, row) in local) {
                val h = hash(row)
                if (before[id] != h) { batch.set(r.collection(t).document(id.toString()), row); k.put(t, id, h); n++; sent++ }
                if (n >= 400) flush()
            }
            for (id in before.keys - local.keys) { batch.delete(r.collection(t).document(id.toString())); k.remove(t, id); n++; sent++; if (n >= 400) flush() }
        }
        flush()
        Cloud.synced(sent)
    }

    private fun pushSettings() {
        val r = root ?: return
        r.collection("meta").document("settings").set(AppSettings.sharedValues()).addOnFailureListener { Cloud.failed(it) }
    }

    /** Sends this phone's settings up (used when this phone's data is chosen as the one to keep). */
    fun uploadSettings() = pushSettings()

    // ───────────── cloud → this phone ─────────────

    private fun pull(t: String, snap: QuerySnapshot) {
        val d = db ?: return; val k = known ?: return
        val cols = columns(d, t)
        val w = d.openHelper.writableDatabase
        pulling = true
        try {
            d.runInTransaction(Runnable {
                for (c in snap.documentChanges) {
                    val doc = c.document
                    if (doc.metadata.hasPendingWrites()) continue
                    val id = doc.id.toLongOrNull() ?: continue
                    if (c.type == DocumentChange.Type.REMOVED) { w.delete("`$t`", "id = ?", arrayOf(id)); k.remove(t, id) }
                    else if (!save(w, k, t, cols, doc)) synchronized(waiting) { waiting += t to doc }
                }
                // a full answer from the server: rows it no longer has were deleted on another phone
                if (!snap.metadata.isFromCache) {
                    val there = snap.documents.mapNotNull { it.id.toLongOrNull() }.toSet()
                    for (id in k.all(t).keys - there) { w.delete("`$t`", "id = ?", arrayOf(id)); k.remove(t, id) }
                }
                // try the rows that were waiting for a parent
                val retry = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
                for ((wt, doc) in retry) if (!save(w, k, wt, columns(d, wt), doc)) synchronized(waiting) { waiting += wt to doc }
            })
        } finally { pulling = false }
        Cloud.synced(0)
    }

    /** Saves one pulled row; false if it has to wait for its parent. */
    private fun save(w: androidx.sqlite.db.SupportSQLiteDatabase, k: Known, t: String, cols: Set<String>, doc: DocumentSnapshot): Boolean {
        val id = doc.id.toLongOrNull() ?: return true
        val row = doc.data.orEmpty().filterKeys { it in cols }.mapValues { (_, v) -> norm(v) } + ("id" to id)
        val h = hash(row)
        if (k.get(t, id) == h) return true
        val cv = ContentValues().apply {
            row.forEach { (c, v) -> when (v) { null -> putNull(c); is Long -> put(c, v); is Double -> put(c, v); else -> put(c, v.toString()) } }
        }
        return try {
            if (w.update("`$t`", SQLiteDatabase.CONFLICT_ABORT, cv, "id = ?", arrayOf(id)) == 0) w.insert("`$t`", SQLiteDatabase.CONFLICT_ABORT, cv)
            k.put(t, id, h); true
        } catch (e: SQLiteConstraintException) { Log.w(TAG, "$t $id waits: ${e.message}"); false }
    }

    // ───────────── helpers ─────────────

    private fun norm(v: Any?): Any? = when (v) {
        null -> null
        is Boolean -> if (v) 1L else 0L
        is Int -> v.toLong()
        is Long -> v
        is Float -> v.toDouble()
        is Double -> v
        else -> v.toString()
    }

    private fun value(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> c.getString(i)
        else -> null // pictures are not stored in the database
    }

    fun rows(d: AppDb, t: String): Map<Long, Map<String, Any?>> {
        val out = LinkedHashMap<Long, Map<String, Any?>>()
        d.openHelper.readableDatabase.query("SELECT * FROM `$t`").use { c ->
            val names = c.columnNames
            while (c.moveToNext()) {
                val row = names.indices.associate { names[it] to value(c, it) }
                (row["id"] as? Long)?.let { out[it] = row }
            }
        }
        return out
    }

    private fun columns(d: AppDb, t: String): Set<String> =
        d.openHelper.readableDatabase.query("PRAGMA table_info(`$t`)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }

    private fun hash(row: Map<String, Any?>): Long {
        val s = row.toSortedMap().entries.joinToString("\u0001") { (k, v) -> k + "=" + when (v) { null -> "~"; is Double -> "d$v"; else -> v.toString() } }
        val b = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return b.take(8).fold(0L) { a, x -> (a shl 8) or (x.toLong() and 0xFF) }
    }

    fun localHasData(ctx: Context): Boolean {
        val d = AppDb.get(ctx)
        return listOf("Student", "Enquiry", "LibraryMember", "Expense").any { t ->
            d.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$t`").use { it.moveToFirst() && it.getLong(0) > 0 }
        }
    }

    /** Empties this phone's records (before taking the cloud's copy). */
    suspend fun wipeLocal(ctx: Context) = withContext(Dispatchers.IO) {
        val d = AppDb.get(ctx)
        d.runInTransaction(Runnable { TABLES.reversed().forEach { d.openHelper.writableDatabase.execSQL("DELETE FROM `$it`") } })
        Known(ctx).clear()
        `in`.vidyadhan.academy.data.Ids.forget()
    }

    fun forgetState(ctx: Context) = Known(ctx).clear()

    /** What each row looked like when it was last the same as in the cloud (a hash per row). */
    private class Known(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "sync.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE k(t TEXT NOT NULL, id INTEGER NOT NULL, h INTEGER NOT NULL, PRIMARY KEY(t, id))") }
        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
        fun all(t: String): Map<Long, Long> = readableDatabase.rawQuery("SELECT id, h FROM k WHERE t = ?", arrayOf(t)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) }
        }
        fun get(t: String, id: Long): Long? = readableDatabase.rawQuery("SELECT h FROM k WHERE t = ? AND id = ?", arrayOf(t, id.toString())).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        fun put(t: String, id: Long, h: Long) { writableDatabase.execSQL("INSERT OR REPLACE INTO k(t, id, h) VALUES(?, ?, ?)", arrayOf<Any>(t, id, h)) }
        fun remove(t: String, id: Long) { writableDatabase.execSQL("DELETE FROM k WHERE t = ? AND id = ?", arrayOf<Any>(t, id)) }
        fun clear() { writableDatabase.execSQL("DELETE FROM k") }
    }
}
