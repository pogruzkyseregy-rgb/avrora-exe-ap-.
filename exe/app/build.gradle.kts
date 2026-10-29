plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Снимки экранов на сервере сборки (workflow «Screens EXE»): так видно новый вид без телефона
    id("io.github.takahirom.roborazzi")
}

// Постоянный ключ подписи. Подходит и aurora.jks, и aurora-debug.jks (ключ плеера).
val keystore = listOf("aurora.jks", "aurora-debug.jks").map { file(it) }.firstOrNull { it.exists() }

android {
    namespace = "ru.avrora.exe"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.avrora.exe"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
        versionName = "exe-12.0"
    }

    signingConfigs {
        create("aurora") {
            if (keystore != null) {
                storeFile = keystore
                storePassword = System.getenv("KS_PASS")
                keyAlias = System.getenv("KS_ALIAS")
                keyPassword = System.getenv("KS_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (keystore != null) signingConfig = signingConfigs.getByName("aurora")
        }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation(bom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.work:work-testing:2.9.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
