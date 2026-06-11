plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Shared library: all the app's logic and UI lives here. The two app modules (:app-zh / :app-ja)
// are thin shells that supply identity (AppConfig), branded resources, and the bundled content DB.
android {
    namespace = "com.tianxian.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        vectorDrawables { useSupportLibrary = true }
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
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
    implementation(libs.mlkit.text.chinese)       // on-device OCR (Chinese; Japanese OCR is a Phase-4 swap)

    implementation(libs.glance.appwidget)         // home-screen widgets (Compose/Glance)
    implementation(libs.work.runtime.ktx)         // periodic review reminders

    testImplementation("junit:junit:4.13.2")       // pure-JVM unit tests (e.g. GlossTest)
}
