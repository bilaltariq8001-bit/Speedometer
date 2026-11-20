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

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        // Map style (vector tiles). OpenFreeMap is free, needs no key, and its terms allow offline area downloads
        // (each at most 3,000 tiles / 50 MB, 4 parallel requests, app User-Agent), which OfflineMaps keeps to.
        // Set MAP_STYLE_URL in local.properties to use another MapLibre style.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val styleUrl = localProps.getProperty("MAP_STYLE_URL")?.takeIf { it.isNotBlank() }
            ?: "https://tiles.openfreemap.org/styles/liberty"
        buildConfigField("String", "MAP_STYLE_URL", "\"$styleUrl\"")
        val admobTestDeviceId = providers.gradleProperty("admobTestDeviceId").orElse("").get()
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "ADMOB_TEST_DEVICE_ID", "\"$admobTestDeviceId\"")
        // Debug builds show Google's test ads (sample app ID, test ad units, every placement on) unless
        // forceTestAds=false is set in gradle.properties; then they use the IDs from Remote Config like release builds.
        val forceTestAds = providers.gradleProperty("forceTestAds").orElse("true").get().toBoolean()
        buildConfigField("boolean", "FORCE_TEST_ADS", "$forceTestAds")
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            include("arm64-v8a", "armeabi-v7a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Release signing reads keystore.properties (git-ignored, see keystore.properties.example). Without the file the
    // release build is unsigned, and nothing secret is ever stored in the project.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("Key/stylishkeyboardstation.keystore")
            storePassword = keystoreProps.getProperty("storePassword") ?: "stylishkeyboardstation"
            keyAlias = keystoreProps.getProperty("keyAlias") ?: "stylishkeyboardstation"
            keyPassword = keystoreProps.getProperty("keyPassword") ?: "stylishkeyboardstation"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // AdMob app ID: Google's sample ID while debug shows test ads (forceTestAds, default true), else the real one.
            val testAdsInDebug = providers.gradleProperty("forceTestAds").orElse("true").get().toBoolean()
            resValue("string", "admob_app_id", if (testAdsInDebug) "ca-app-pub-3940256099942544~3347511713" else "ca-app-pub-7094796830915786~9068896024")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            // Real AdMob app ID (fastTest inherits it).
            resValue("string", "admob_app_id", "ca-app-pub-7094796830915786~9068896024")
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
        resValues = true
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
    implementation(libs.okhttp)
    implementation(libs.lottie.compose)
    implementation(libs.ads.mobile.sdk)
    implementation(libs.ump)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    // Installs the baseline profiles (the app's own and the ones Compose ships) on sideloaded installs too, so code runs
    // AOT-compiled from the first launch instead of interpreted. Play installs them by itself.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}

composeCompiler {
    // Classes the compiler cannot prove immutable but which are never mutated here, so composables taking them can skip.
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("compose_stability.conf")
}
