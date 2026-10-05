plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.crm.phoneagent"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.crm.phoneagent"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Server environment per build variant (Phase 0 requirement: dev/staging/production without
    // per-phone source changes). These are only the DEFAULT the Welcome screen pre-fills - the
    // rep can still type a different server URL before pairing, and it is stored on-device
    // (SecureDeviceStore), never rebuilt into the APK per phone.
    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"http://10.0.2.2:8080/api/v1\"")
            buildConfigField("String", "DEFAULT_WS_BASE_URL", "\"ws://10.0.2.2:8080\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"https://crm.example.com/api/v1\"")
            buildConfigField("String", "DEFAULT_WS_BASE_URL", "\"wss://crm.example.com\"")
        }
    }

    packaging {
        resources.excludes.add("META-INF/*.md")
        resources.excludes.add("META-INF/LICENSE*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
