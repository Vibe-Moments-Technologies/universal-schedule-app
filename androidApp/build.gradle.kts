import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.foundation)
    implementation(libs.ktor.client.okhttp)
}

android {
    namespace = "com.jetbrains.kmpapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ru.vibemoments.krasava"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (project.findProperty("buildNumber") as? String)?.toIntOrNull()
            ?: System.getenv("BUILD_NUMBER")?.toIntOrNull()
            ?: 32
        // CI подставляет версию канала через tools/versioning.py; в репо — базовая версия линии разработки
        versionName = "26.0.0"
    }
    signingConfigs {
        // Релизный ключ НЕ в репо: CI подаёт его из секретов org
        // (RELEASE_KEYSTORE_B64 + пароли). Локально — из переменных окружения.
        create("release") {
            val b64 = System.getenv("RELEASE_KEYSTORE_B64")
            if (b64 != null) {
                storeFile = file("${layout.buildDirectory.get().asFile}/release.keystore").apply {
                    parentFile.mkdirs()
                    writeBytes(Base64.getDecoder().decode(b64))
                }
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "krasava"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            // CI (с секретами) — релизный ключ; локально — стандартный debug SDK.
            if (System.getenv("RELEASE_KEYSTORE_B64") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("RELEASE_KEYSTORE_B64") != null) {
                signingConfigs.getByName("release")
            } else {
                null // Без секретов release не собирается — fail-fast в CI.
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
