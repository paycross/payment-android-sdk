import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Staging M2M credentials for the demo's session minting. Gitignored;
// copy creds.properties.example and fill in real values.
val creds = Properties().apply {
    val file = rootProject.file("demo-app/creds.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun cred(key: String): String = "\"${creds.getProperty(key, "")}\""

android {
    namespace = "com.paycross.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.paycross.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "TOKEN_URL", cred("TOKEN_URL"))
        buildConfigField("String", "CLIENT_ID", cred("CLIENT_ID"))
        buildConfigField("String", "CLIENT_SECRET", cred("CLIENT_SECRET"))
        buildConfigField("String", "PAYMENT_API_URL", cred("PAYMENT_API_URL"))
        buildConfigField("String", "PAYCROSS_VERSION", cred("PAYCROSS_VERSION"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }
}

dependencies {
    implementation(project(":sdk"))

    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
