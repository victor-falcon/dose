import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    // Kotlin support is built into AGP 9+, so no kotlin-android plugin here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Signing credentials come from ~/.gradle/gradle.properties (or env vars on CI), never from the
 * repo. If they're missing the release build stays unsigned rather than failing, so a checkout
 * without the key can still build and test.
 */
fun secret(name: String): String? =
    (project.findProperty(name) as String?) ?: System.getenv(name)

val uploadKeystore: File? = secret("DOSE_STORE_FILE")?.let(::File)?.takeIf { it.exists() }

android {
    namespace = "com.victorfalcon.dose"
    compileSdk = 37

    defaultConfig {
        // Reverse-DNS of victorfalcon.es (the domain we actually own), and it matches the
        // package reserved on Play. Permanent once published — the code package stays
        // com.victorfalcon.dose, which AGP allows and nothing public sees.
        applicationId = "es.victorfalcon.dose"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKeystore != null) {
            create("release") {
                storeFile = uploadKeystore
                storePassword = secret("DOSE_STORE_PASSWORD")
                keyAlias = secret("DOSE_KEY_ALIAS")
                keyPassword = secret("DOSE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric needs the merged resources
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// No jvmToolchain: compile with the JDK already running Gradle (Android Studio's
// JBR is 17+), so no toolchain download is needed.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ponytail: Hilt's separate aggregating task can't find the KSP-generated
// _ComponentTreeDeps under AGP 9 + KSP. Folding aggregation into the normal
// processing step is the deterministic fix. Re-enable if incremental build
// speed ever matters and the AGP-9 interaction is fixed upstream.
hilt {
    enableAggregatingTask = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Reminders
    implementation(libs.androidx.work.runtime.ktx)

    // Widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
