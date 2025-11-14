// ---- imports MUST be at the very top ----
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    // id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1"
}

// Optional: exclude the litert group everywhere
configurations.configureEach {
    exclude(group = "com.google.ai.edge.litert")
}

/* ------ local.properties helper ------- */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun lp(name: String) = localProps.getProperty(name) ?: ""

android {
    namespace = "com.example.gestura"
    compileSdk = 36 // Use a released SDK. Bump to 36 when it’s officially available in your SDK Manager.

    defaultConfig {
        applicationId = "com.example.gestura"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GENASL_API_BASE", "\"${lp("GENASL_API_BASE")}\"")
        buildConfigField("String", "LARA_ACCESS_KEY_ID", "\"${lp("LARA_ACCESS_KEY_ID")}\"")
        buildConfigField("String", "LARA_ACCESS_KEY_SECRET", "\"${lp("LARA_ACCESS_KEY_SECRET")}\"")
        buildConfigField("String", "GENASL_BASE_URL", "\"${lp("GENASL_BASE_URL")}\"")
        buildConfigField("String", "GENASL_API_KEY", "\"${lp("GENASL_API_KEY")}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${lp("OPENAI_API_KEY")}\"")
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // nothing special
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    // Use Java 17 with AGP 8+
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Let the version come from libs.versions.toml if you track it there.
    // If you pin it, be sure it matches your Kotlin plugin version.
    // composeOptions { kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get() }
}

dependencies {
    // --- Kotlin + AndroidX core (from your version catalog) ---
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)

    // --- Compose (use BOM; no per-artifact versions) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)   // Activity-Compose bridge
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation("androidx.compose.material:material-icons-extended")

    // --- Navigation ---
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // --- Firebase ---
    implementation(libs.firebase.auth)

    // --- Networking + JSON ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.json:json:20240303")

    // --- Video playback (ExoPlayer 2.x; keep this OR Media3, not both) ---
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    // --- MediaPipe Tasks (vision) ---
    implementation("com.google.mediapipe:tasks-vision:0.10.9")

    // --- TensorFlow Lite (use one consistent version family) ---

    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // --- LARA SDK ---
    implementation("com.translated.lara:lara-sdk:1.4.3")

    // --- Material (View system) if you still use XML/views alongside Compose ---
    implementation(libs.material)

    // --- Other (keep only if actually used) ---
    implementation(libs.genai.common)
    implementation(libs.androidx.room.external.antlr)

    // --- Tests ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("org.tensorflow:tensorflow-lite:2.9.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.9.0") // If you are using GPU delegation
}

