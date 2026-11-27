// Top-level build file — plugins are declared here and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version libs.versions.agp.get() apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
