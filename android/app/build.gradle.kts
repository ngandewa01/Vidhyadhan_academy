plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.vidyadhan.academy"
    compileSdk = 37

    defaultConfig {
        applicationId = "in.vidyadhan.academy"
        minSdk = 26
        targetSdk = 36
        versionCode = 41
        versionName = "0.6.0"
        // Firebase (cloud sync): read from app/google-services.json when it is there. Without it the app works as before, phone only.
        val gs = file("google-services.json")
        if (gs.exists()) {
            @Suppress("UNCHECKED_CAST")
            val j = groovy.json.JsonSlurper().parse(gs) as Map<String, Any?>
            val info = j["project_info"] as Map<String, Any?>
            val client = (j["client"] as List<Map<String, Any?>>).first {
                ((it["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>)["package_name"] == "in.vidyadhan.academy"
            }
            val appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"].toString()
            val apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"].toString()
            val web = (client["oauth_client"] as? List<Map<String, Any?>>).orEmpty().firstOrNull { it["client_type"].toString() == "3" }?.get("client_id")?.toString()
                ?: ((((client["services"] as? Map<String, Any?>)?.get("appinvite_service") as? Map<String, Any?>)?.get("other_platform_oauth_client") as? List<Map<String, Any?>>)
                    .orEmpty().firstOrNull { it["client_type"].toString() == "3" }?.get("client_id")?.toString()).orEmpty()
            resValue("string", "google_app_id", appId)
            resValue("string", "google_api_key", apiKey)
            resValue("string", "project_id", info["project_id"].toString())
            resValue("string", "gcm_defaultSenderId", info["project_number"].toString())
            resValue("string", "google_storage_bucket", info["storage_bucket"]?.toString().orEmpty())
            resValue("string", "default_web_client_id", web)
            buildConfigField("String", "WEB_CLIENT_ID", "\"$web\"")
        } else buildConfigField("String", "WEB_CLIENT_ID", "\"\"")
    }
    // Release key: given by the build server through environment variables (never stored in the repo).
    val keystore = System.getenv("SIGNING_KEYSTORE")?.takeIf { file(it).exists() }
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storeType = "pkcs12"
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = System.getenv("SIGNING_ALIAS") ?: "vidhyadhan"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; resValues = true; buildConfig = true }
    // Room schema files, so the database-upgrade test can read them (debug builds only)
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("shots.dir", System.getenv("SHOTS_DIR") ?: "build/shots") }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.core.ktx)
    implementation(libs.coroutines)
    implementation(libs.datastore)
    implementation(libs.fragment)
    implementation(libs.biometric)
    implementation(libs.work.runtime)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.config)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation(libs.coroutines.play)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.test.core)
    testImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
}
