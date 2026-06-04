import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is pulled from a local (git-ignored) keystore.properties or from environment
// variables (CI secrets). Nothing secret is committed. With neither present, `assembleRelease`
// falls back to debug signing so it still produces an installable APK for quick testing.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)
val hasReleaseKeystore = signingValue("storeFile", "KEYSTORE_FILE") != null

android {
    namespace = "com.scholar.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scholar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0"
        vectorDrawables { useSupportLibrary = true }
    }
    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                // resolve relative paths (keystore.properties) against the repo root; absolute CI paths pass through
                storeFile = rootProject.file(signingValue("storeFile", "KEYSTORE_FILE")!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // ship the prebuilt content DB uncompressed so it can be copied out of assets
    androidResources { noCompress += "db" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.tooling.prev)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)

    implementation(libs.pdfbox.android)          // PDF text extraction
    implementation(libs.mlkit.text.chinese)       // on-device OCR for scanned pages

    implementation(libs.glance.appwidget)         // home-screen widgets (Compose/Glance)
    implementation(libs.work.runtime.ktx)         // periodic review reminders
}
