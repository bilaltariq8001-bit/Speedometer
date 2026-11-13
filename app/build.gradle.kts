import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Firebase (Remote Config, Analytics) only switches on when google-services.json from the Firebase console is in app/.
// Without the file the app builds and runs: ads read their in-app defaults and no analytics are sent.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.speedometer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.speedometer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Map tile source. OpenStreetMap's public servers are for light use only; set TILE_URL in local.properties
        // to another raster tile server before release. {z}, {x} and {y} are filled in by the map.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val tileUrl = localProps.getProperty("TILE_URL")?.takeIf { it.isNotBlank() }
            ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        buildConfigField("String", "TILE_URL", "\"$tileUrl\"")
        val admobTestDeviceId = providers.gradleProperty("admobTestDeviceId").orElse("").get()
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "ADMOB_TEST_DEVICE_ID", "\"$admobTestDeviceId\"")
    }

    // Release signing reads keystore.properties (git-ignored, see keystore.properties.example). Without the file the
    // release build is unsigned, and nothing secret is ever stored in the project.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Native symbols (MapLibre) go into the bundle so Play can symbolicate native crashes.
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
        }
        // A release-like build for testing speed on a phone: shrunk and optimised like the store build, but signed with the
        // debug key so it installs over the debug app. Debug builds run Compose and the map noticeably slower.
        create("fastTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            ndk.debugSymbolLevel = null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    bundle {
        // The app switches language itself (Settings > Language), so every language must ship in every install.
        language { enableSplit = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.constraintlayout.compose)
    implementation(libs.sdp.ssp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.maplibre)
    implementation(libs.lottie.compose)
    implementation(libs.ads.mobile.sdk)
    implementation(libs.ump)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
