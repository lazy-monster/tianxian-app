import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Tianxian (天仙) — the Chinese reading app. A thin shell over :core: it supplies the app
// identity (AppConfig), the launcher branding, and the bundled Chinese content DB.

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
    namespace = "com.tianxian.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tianxian.app"
        minSdk = 26
        targetSdk = 35
        // v0.11.0 shipped with versionName left at 0.10.5, so installs report 0.10.5 and the
        // updater nags forever (latest tag v0.11.0 > 0.10.5). 0.11.1 is the corrected next release;
        // CI overrides these from the pushed tag so name and tag can never drift again (release.yml).
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 12
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.11.1"
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
        debug {
            // Debug builds install as a separate app (com.tianxian.app.debug). Debug keystores
            // differ per machine and per CI runner, so a debug build can never *update* a
            // release install — without this suffix Android rejects it with "package conflicts
            // with existing package". Side-by-side install sidesteps the clash entirely.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // ship the prebuilt content DB uncompressed so it can be copied out of assets
    androidResources { noCompress += "db" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":core"))
}
