package `in`.vidyadhan.academy

import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    /** Google sign-in needs the web client id from google-services.json built into the app. */
    @Test fun googleSignInClientIdIsBuiltIn() {
        assertTrue("WEB_CLIENT_ID missing: '${BuildConfig.WEB_CLIENT_ID}'", BuildConfig.WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com"))
    }
}
