package `in`.vidyadhan.academy.cloud

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import `in`.vidyadhan.academy.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The phones signed in to the academy's account: academies/{uid}/devices/{installId}.
 * The academy document's "mainDevice" names the one phone that may disconnect the others or hand "main" over.
 */
object Devices {
    data class Device(val id: String, val model: String, val version: String, val lastSeen: Long, val kicked: Boolean)

    var list by mutableStateOf<List<Device>>(emptyList()); private set
    var mainId by mutableStateOf(""); private set
    val me: String get() = AppSettings.installId
    val isMain: Boolean get() = mainId == me

    private var root: DocumentReference? = null
    private val regs = mutableListOf<ListenerRegistration>()
    private var lastBeat = 0L

    fun model(): String {
        val m = Build.MODEL.orEmpty(); val b = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        return if (m.startsWith(b, true)) m else "$b $m".trim()
    }

    fun start(ctx: Context, uid: String) {
        stop()
        val r = runCatching { FirebaseFirestore.getInstance().collection("academies").document(uid) }.getOrNull() ?: return
        root = r
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        val mine = r.collection("devices").document(me)
        mine.set(mapOf("model" to model(), "version" to version, "lastSeen" to FieldValue.serverTimestamp()), SetOptions.merge())
        lastBeat = System.currentTimeMillis()
        // the first phone to come along becomes the main device
        FirebaseFirestore.getInstance().runTransaction { tr ->
            if (tr.get(r).getString("mainDevice").isNullOrBlank()) tr.set(r, mapOf("mainDevice" to me), SetOptions.merge())
        }.addOnFailureListener { Log.w("Devices", "main", it) }
        regs += r.addSnapshotListener { snap, _ -> mainId = snap?.getString("mainDevice").orEmpty() }
        regs += r.collection("devices").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            list = snap.documents.map { d ->
                Device(d.id, d.getString("model").orEmpty().ifBlank { "Phone" }, d.getString("version").orEmpty(),
                    d.getTimestamp("lastSeen")?.toDate()?.time ?: 0L, d.getBoolean("kick") == true)
            }.sortedWith(compareBy({ it.id != mainId }, { -it.lastSeen }))
            // disconnected by the main device: sign out here (records stay on this phone)
            if (list.any { it.id == me && it.kicked } && !snap.metadata.hasPendingWrites()) {
                CoroutineScope(Dispatchers.Main).launch { Cloud.signOut(ctx, kicked = true) }
            }
        }
    }

    /** Keeps "last active" fresh: at most every 10 minutes, after a sync. */
    fun beat() {
        val r = root ?: return
        if (System.currentTimeMillis() - lastBeat < 10 * 60_000L) return
        lastBeat = System.currentTimeMillis()
        r.collection("devices").document(me).set(mapOf("lastSeen" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    /** Screenshot tests only. */
    fun preview(devices: List<Device>, main: String) { list = devices; mainId = main }

    fun stop() { regs.forEach { it.remove() }; regs.clear(); root = null; list = emptyList(); mainId = "" }

    /** Before signing out: take this phone off the list; if it was main, the next phone to open becomes main. */
    suspend fun leave() {
        val r = root ?: return
        runCatching {
            if (isMain) r.update("mainDevice", "").await()
            r.collection("devices").document(me).delete().await()
        }.onFailure { Log.w("Devices", "leave", it) }
    }

    fun disconnect(id: String) { if (isMain && id != me) root?.collection("devices")?.document(id)?.set(mapOf("kick" to true), SetOptions.merge()) }
    fun remove(id: String) { if (isMain && id != me) root?.collection("devices")?.document(id)?.delete() }
    fun makeMain(id: String) { if (isMain) root?.update("mainDevice", id) }
}
