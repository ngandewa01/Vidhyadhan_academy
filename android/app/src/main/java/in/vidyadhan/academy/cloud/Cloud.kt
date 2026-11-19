package `in`.vidyadhan.academy.cloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import `in`.vidyadhan.academy.AppSettings
import `in`.vidyadhan.academy.data.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Cloud sync with Firebase: the owner signs in with Google, and the academy's records are kept the same on
 * every phone signed in to that account. Works only in builds made with the academy's google-services.json.
 */
object Cloud {
    enum class State { NotSetUp, SignedOut, Choosing, Working, On, Error }

    var state by mutableStateOf(State.SignedOut); private set
    var email by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    var lastSync by mutableStateOf(0L); private set

    private val main = Handler(Looper.getMainLooper())
    private fun ui(f: () -> Unit) = if (Looper.myLooper() == Looper.getMainLooper()) f() else { main.post(f); Unit }

    fun available(ctx: Context): Boolean = runCatching { FirebaseApp.getApps(ctx).isNotEmpty() || FirebaseApp.initializeApp(ctx) != null }.getOrDefault(false)

    /** At app start: carry on syncing if this phone was set up before. */
    fun start(ctx: Context) {
        if (!available(ctx)) { state = State.NotSetUp; return }
        val user = FirebaseAuth.getInstance().currentUser
        lastSync = AppSettings.cloudLast
        if (user == null) { state = State.SignedOut; return }
        email = user.email.orEmpty()
        if (AppSettings.cloudAcademy.isBlank()) { state = State.SignedOut; return } // the first-time choice was never finished
        Ids.device = AppSettings.cloudDevice
        Sync.start(ctx, AppSettings.cloudAcademy)
        state = State.On
    }

    /** Google sign-in, then the first sync. Returns an error to show, or null. */
    suspend fun signIn(activity: Context): String? {
        if (!available(activity)) return "Cloud is not set up in this version of the app."
        state = State.Working; message = "Signing in…"
        return try {
            val webId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName).takeIf { it != 0 }?.let { activity.getString(it) }.orEmpty()
            require(webId.isNotBlank()) { "Google sign-in is not turned on in Firebase yet (no web client id)." }
            val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(webId).setAutoSelectEnabled(false).build()
            val res = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val c = res.credential
            require(c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Please choose a Google account." }
            val token = GoogleIdTokenCredential.createFrom(c.data).idToken
            val user = FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user ?: error("Sign-in failed")
            email = user.email.orEmpty()
            prepare(activity, user.uid)
            null
        } catch (e: GetCredentialCancellationException) {
            state = State.SignedOut; null
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            state = State.SignedOut; "No Google account to choose. Add your Gmail in phone Settings → Accounts, and update Google Play services, then try again."
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            Log.w("Cloud", "sign in", e)
            state = State.SignedOut; "Google sign-in failed (${e.type.substringAfterLast('.')}): ${e.message ?: "no details"}"
        } catch (e: Exception) {
            Log.w("Cloud", "sign in", e)
            state = State.SignedOut; "Sign-in failed (${e.javaClass.simpleName}): ${e.message ?: "no details"}"
        }
    }

    /** First sync of this phone: if both this phone and the cloud already have records, the owner chooses. */
    private suspend fun prepare(ctx: Context, uid: String) {
        message = "Checking the cloud…"
        val root = FirebaseFirestore.getInstance().collection("academies").document(uid)
        val cloudHas = listOf("Student", "Enquiry", "LibraryMember", "Expense").any { t ->
            !root.collection(t).limit(1).get(Source.SERVER).await().isEmpty
        }
        val localHas = withContext(Dispatchers.IO) { Sync.localHasData(ctx) }
        pending = uid
        when {
            cloudHas && localHas -> { state = State.Choosing; message = "" }
            cloudHas -> finish(ctx, keepCloud = true)
            else -> finish(ctx, keepCloud = false)
        }
    }
    private var pending: String? = null

    /** [keepCloud]: take the cloud's records on this phone; otherwise this phone's records become the cloud's. */
    suspend fun finish(ctx: Context, keepCloud: Boolean) {
        val uid = pending ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        state = State.Working
        try {
            val fs = FirebaseFirestore.getInstance()
            val root = fs.collection("academies").document(uid)
            // each phone gets its own block of record numbers: the phone whose records are kept is phone 0
            val device = fs.runTransaction { tr ->
                val n = tr.get(root).getLong("phones") ?: 0L
                val mine = if (keepCloud) maxOf(n, 1L) else 0L
                tr.set(root, mapOf("phones" to maxOf(n, mine + 1), "owner" to email, "updated" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
                mine
            }.await()
            if (keepCloud) {
                message = "Downloading your records…"
                Sync.wipeLocal(ctx)
            } else {
                message = "Uploading this phone's records…"
                Sync.forgetState(ctx)
                for (t in Sync.TABLES) {
                    val docs = root.collection(t).get(Source.SERVER).await().documents
                    docs.chunked(400).forEach { part -> fs.batch().apply { part.forEach { delete(it.reference) } }.commit().await() }
                }
            }
            AppSettings.cloudDevice = device
            AppSettings.cloudAcademy = uid
            Ids.device = device
            Sync.start(ctx, uid)
            if (!keepCloud) Sync.uploadSettings()
            state = State.On; message = ""
        } catch (e: Exception) {
            Log.w("Cloud", "first sync", e)
            state = State.Error; message = e.message ?: "Could not reach the cloud"
        }
        pending = null
    }

    suspend fun signOut(ctx: Context) {
        Sync.stop()
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) }
        AppSettings.cloudAcademy = ""
        Sync.forgetState(ctx)
        state = State.SignedOut; email = ""
    }

    internal fun synced(sent: Int) = ui {
        lastSync = System.currentTimeMillis(); AppSettings.cloudLast = lastSync
        if (state == State.Error) { state = State.On; message = "" }
    }

    internal fun failed(e: Throwable) = ui {
        Log.w("Cloud", "sync", e)
        if (state == State.On) { state = State.Error; message = e.message ?: "Sync problem" }
    }
}
