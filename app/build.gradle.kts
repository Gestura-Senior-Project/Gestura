// ---- imports MUST be at the very top ----
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
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
    compileSdk = 36 

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
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // --- Kotlin + AndroidX core ---
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation("androidx.compose.material:material-icons-extended")

    // --- Navigation ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.google.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // --- Networking + JSON ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.json:json:20240303")

    // --- Media3 (ExoPlayer) ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // --- Material ---
    implementation(libs.material)

    // --- ML / MediaPipe ---
    implementation("org.tensorflow:tensorflow-lite:2.10.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // --- Others ---
    implementation(libs.lara.sdk)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.ext)
    androidTestImplementation(libs.androidx.espresso.core)
}
