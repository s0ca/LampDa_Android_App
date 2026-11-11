plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersion = "0.4.3-beta.1"
val betaApp = appVersion.substringAfter('-', "").startsWith("beta")

android {
    namespace = "com.lampda.app"
    compileSdk = 35

    defaultConfig {
        applicationId = if (betaApp) "com.lampda.app.beta" else "com.lampda.app"
        manifestPlaceholders["appLabel"] = if (betaApp) "Lamp-Da Beta" else "Lamp-Da"
        minSdk = 26
        targetSdk = 35
        versionCode = 36
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir("../../licenses")

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
    implementation("androidx.documentfile:documentfile:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
}
