import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { input -> load(input) }
    }
}

val arCoreApiKey = localProperties.getProperty("ARCORE_API_KEY")
    ?: providers.gradleProperty("ARCORE_API_KEY").orNull
    ?: providers.environmentVariable("ARCORE_API_KEY").orNull
    ?: ""

android {
    namespace = "com.histoury.app"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.histoury.app"

        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // Supply this as ARCORE_API_KEY in ignored local.properties, user
        // Gradle properties, or the environment. Do not commit the key.
        manifestPlaceholders["ARCORE_API_KEY"] = arCoreApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // SceneView/Filament are compiled against Java 17 and won't link
        // against an 11 target. With AGP 9's built-in Kotlin, jvmTarget
        // follows targetCompatibility automatically — no kotlin {} block
        // needed.
        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.compose.material3)

    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))

    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.compose.foundation:foundation")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.firebase:firebase-auth")

    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.firebase:firebase-storage")

    implementation("com.google.firebase:firebase-functions")

    implementation("com.google.maps.android:maps-compose:4.3.3")

    implementation("com.google.android.gms:play-services-maps:18.2.0")

    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Filament-backed 3D/AR renderer with a Compose ARScene, so the AR
    // screen is an ordinary NavHost destination instead of a separate
    // Activity.
    //
    // Pinned to the 2.x line on purpose. SceneView 4.x is compiled with
    // Kotlin 2.4; AGP 9.0's built-in Kotlin compiler is 2.2 and cannot
    // read 2.4 class metadata — and the newer kotlin-stdlib 4.x drags in
    // breaks name resolution across the entire project (the "hundreds of
    // Unresolved reference errors" failure). 2.3.3 is the newest release
    // this toolchain can consume. Do not bump this past 2.x without also
    // moving to a toolchain on Kotlin 2.4+.
    //
    // ARCore (Session, Config, Earth, Anchor) comes in transitively — do
    // not add com.google.ar:core separately, or Gradle may resolve a newer
    // ARCore than this SceneView was built against.
    implementation("io.github.sceneview:arsceneview:2.3.3")

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
}
