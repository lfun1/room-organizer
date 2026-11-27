// =============================================================================
// Top-level (project-wide) build file
// =============================================================================
// This file DECLARES which Gradle plugins exist in the project, without
// turning them on here. `apply false` means "make this plugin available, but
// don't activate it at the top level". Each module (we only have one: `app`)
// then switches on the plugins it needs in its own build.gradle.kts.
//
// The `libs.plugins.xxx` names come from gradle/libs.versions.toml.
// =============================================================================
plugins {
    alias(libs.plugins.android.application) apply false  // builds an Android app
    alias(libs.plugins.kotlin.compose) apply false       // compiles Jetpack Compose UI code

    // NEW for v0: KSP, the code generator Room uses to write database code
    alias(libs.plugins.ksp) apply false
}
