plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.anilumos.manga.engine"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
configurations.configureEach {
    if (name.contains("UnitTest", ignoreCase = true)) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.squareup.okhttp3:okhttp"))
                .using(module("com.squareup.okhttp3:okhttp-jvm:5.1.0"))
            substitute(module("com.squareup.okhttp3:okhttp-android"))
                .using(module("com.squareup.okhttp3:okhttp-jvm:5.1.0"))
        }
    }
}
dependencies {
    // Same commit as Kuroha's declared 3432a85ac0 dependency; canonical repository.
    implementation("com.github.Kotatsu-Redo:kotatsu-parsers-redo:3432a85ac02ce99824cf841aca40bfd3af78ed3f") {
        exclude(group = "org.json", module = "json")
    }
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.squareup.okio:okio:3.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
}
