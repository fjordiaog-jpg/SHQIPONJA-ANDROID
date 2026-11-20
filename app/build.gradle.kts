
plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("com.google.gms.google-services") version "4.4.2"
}

android {
    namespace = "com.shqiponja.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shqiponja.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    implementation("androidx.core:core:1.15.0")

    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // ZEGO Audio and Video Calls
    implementation("im.zego:zego_uikit_prebuilt_call_android:+")
}
