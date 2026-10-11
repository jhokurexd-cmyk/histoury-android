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

// ----- Map configuration (see MAPLIBRE_MIGRATION.md) -----------------------
// Read like ARCORE_API_KEY: local.properties, then Gradle properties, then
// environment variables, then the default. None of these are secrets
// (OpenFreeMap needs no key); a provider that does need one should put it
// in local.properties, which is never committed.
fun mapSetting(name: String, default: String): String =
    localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: default

// "maplibre" (OpenStreetMap map, the default) or "google" (the previous
// Google Maps renderer, kept until MapLibre is signed off on devices).
val histouryMapRenderer = mapSetting("HISTOURY_MAP_RENDERER", "maplibre")

// OpenFreeMap: free, no API key, commercial use allowed, but no SLA. For the
// Play Store release, point these at a provider with an SLA or at self-hosted
// tiles (see MAPLIBRE_MIGRATION.md, "Before a public release").
val histouryMapStyleLight = mapSetting("HISTOURY_MAP_STYLE_LIGHT", "https://tiles.openfreemap.org/styles/liberty")
val histouryMapStyleDark = mapSetting("HISTOURY_MAP_STYLE_DARK", "https://tiles.openfreemap.org/styles/dark")
val histouryMapAttribution = mapSetting(
    "HISTOURY_MAP_ATTRIBUTION",
    "\u00a9 OpenFreeMap \u00a9 OpenMapTiles \u00a9 OpenStreetMap contributors"
)

fun quoted(value: String) = "\"" + value.replace("\"", "\\\"") + "\""

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
        versionName = "1.0.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // Supply this as ARCORE_API_KEY in ignored local.properties, user
        // Gradle properties, or the environment. Do not commit the key.
        manifestPlaceholders["ARCORE_API_KEY"] = arCoreApiKey

        buildConfigField("String", "HISTOURY_MAP_RENDERER", quoted(histouryMapRenderer))
        buildConfigField("String", "HISTOURY_MAP_STYLE_LIGHT", quoted(histouryMapStyleLight))
        buildConfigField("String", "HISTOURY_MAP_STYLE_DARK", quoted(histouryMapStyleDark))
        buildConfigField("String", "HISTOURY_MAP_ATTRIBUTION", quoted(histouryMapAttribution))
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
        // MapConfig reads the map settings above from BuildConfig.
        buildConfig = true
    }

    packaging {
        jniLibs {
            // MapLibre and SceneView/Filament both ship native code. If both
            // ever bundle the shared C++ runtime, keep one copy instead of
            // failing the build.
            pickFirsts += "**/libc++_shared.so"
        }
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

    implementation("androidx.work:work-runtime-ktx:2.10.1")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.compose.foundation:foundation")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.firebase:firebase-auth")

    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.firebase:firebase-storage")

    implementation("com.google.firebase:firebase-functions")

    // Maps SDK 19.x: the current Google base map (same palette and data
    // layers as the Google Maps app, incl. sidewalks/crosswalks/footpath
    // detail where Google has it, from zoom ~17) and the native dark color
    // scheme. 18.2.0 predates both. maps-compose 6.12.2 is the last
    // release on Maps SDK 19.2.0 (Kotlin 2.2.20, Compose BOM 2025.09):
    // 7.0 raises the Compose version and 8.0 moves to Maps SDK 20, so this
    // is the smallest step that brings the current base map.
    implementation("com.google.maps.android:maps-compose:6.12.2")

    implementation("com.google.android.gms:play-services-maps:19.2.0")

    implementation("com.google.android.gms:play-services-location:21.2.0")

    // MapLibre Native: the open-source map renderer for OpenStreetMap-based
    // vector tiles (MapConfig picks it by default). The OpenGL ES build, not
    // the default artifact: since 13.0 `android-sdk` renders with Vulkan,
    // whose drivers are still patchy on older Android 8-10 phones. 13.6.1 is
    // the current stable release; minSdk 23, so it fits our minSdk 26.
    // The Google Maps SDK above stays until MapLibre is signed off on devices
    // (and LatLng from it is still used as a plain model class).
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")

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
