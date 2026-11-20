package `in`.vidyadhan.academy.ai

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import `in`.vidyadhan.academy.AppSettings

/**
 * The Gemini key set once by the owner in the Firebase console (Remote Config, parameter "gemini_key").
 * Every phone gets it automatically, also after a reinstall, without signing in; the app never lets anyone edit it.
 */
object RemoteKey {
    const val PARAM = "gemini_key"

    fun fetch(ctx: Context) {
        runCatching {
            if (FirebaseApp.getApps(ctx).isEmpty() && FirebaseApp.initializeApp(ctx) == null) return
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600).build())
            rc.fetchAndActivate().addOnCompleteListener {
                val k = rc.getString(PARAM).trim()
                if (k.startsWith("AIza") && k != AppSettings.geminiKey) {
                    AppSettings.geminiKey = k; AppSettings.geminiModel = ""; AppSettings.geminiFromFirebase = true
                }
                if (!it.isSuccessful) Log.w("RemoteKey", "fetch failed", it.exception)
            }
        }.onFailure { Log.w("RemoteKey", "remote config", it) }
    }
}
