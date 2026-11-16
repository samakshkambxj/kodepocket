// The Kotlin Compose plugin below pins KGP 2.3.21, which AGP 9's built-in
// Kotlin picks up through buildscript classpath resolution.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
