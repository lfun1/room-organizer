// =============================================================================
// App module build file
// =============================================================================
// This file controls HOW the app is built:
//   - which plugins run (Android, Compose, KSP)
//   - the app's identity (applicationId) and version numbers
//   - which libraries are bundled into the app (`dependencies { }`)
//
// After editing this file, Android Studio shows a "Sync Now" bar at the top.
// Click it so Android Studio downloads any new libraries.
// =============================================================================

plugins {
    alias(libs.plugins.android.application)  // this module is an Android app
    alias(libs.plugins.kotlin.compose)       // enables the Compose compiler

    // NEW for v0: switch on KSP so Room's code generator runs during the build
    alias(libs.plugins.ksp)
}

android {
    // `namespace` = the Kotlin/Java package where the app's code lives.
    namespace = "com.lbunny.roomorganizer"
    compileSdk {
        version = release(37)  // build against the Android 17 (API 37) SDK
    }

    defaultConfig {
        // The app's permanent, unique ID on the phone. NEVER change this,
        // or the phone will treat it as a different app and the existing
        // database will not carry over.
        applicationId = "com.lbunny.roomorganizer"

        minSdk = 26     // the oldest Android version supported: 8.0
        targetSdk = 37  // the newest Android version the app is tested against

        // versionCode = an internal whole number. It must go UP with every
        //               release so Android installs it as an upgrade.
        // versionName = the human-friendly label shown in Settings > Apps.
        // Bump these for every release. History:
        //   v0 = code 1, name "0.1"
        //   v1 = code 2, name "1.0"  (search, filters, categories/quantity/condition, swipe)
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Shrinks/optimizes the app for a "release" build. We use
            // "debug" builds while developing, so this doesn't affect us yet.
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true  // turn on Jetpack Compose for this module
    }
}

// -----------------------------------------------------------------------------
// NEW for v0: Room "schema export"
// -----------------------------------------------------------------------------
// Every time the database layout (its "schema": tables + columns) is built,
// Room writes a JSON snapshot of it to RoomOrganizer/app/schemas/.
// e.g. schemas/.../AppDatabase/1.json  for database version 1 (our v0).
//
// Why this matters for future app versions: when v1 adds fields like
// category/quantity/condition, Room compares the old snapshot (1.json) with
// the new one (2.json) and can generate the upgrade ("migration") for us,
// keeping all existing folders and items. Keep this folder; don't delete it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ---- From the template: Compose UI + basic Android libraries ----------
    // `platform(compose.bom)` sets matching versions for all Compose libraries
    // below, which is why those lines don't need their own version numbers.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ---- NEW for v0 ---------------------------------------------------------
    // Room (the SQLite database).
    //   implementation = bundled into the app
    //   ksp            = only used at BUILD time to generate code; not shipped
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Moving between screens (Home -> folder -> details) + Back gesture
    implementation(libs.androidx.navigation.compose)

    // ViewModels inside Compose screens (hold each screen's data)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coil: shows photos of items/folders
    implementation(libs.coil.compose)

    // The full set of Material icons (folder, box, move, ⋮ menu, ...)
    implementation(libs.androidx.compose.material.icons.extended)

    // ---- Testing libraries (from the template; only used by test code) -----
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Debug-only tools (e.g. Android Studio's Layout Inspector and @Preview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
