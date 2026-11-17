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
        versionCode = 8
        versionName = "0.5.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
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
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.test.core)
    testImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
}
