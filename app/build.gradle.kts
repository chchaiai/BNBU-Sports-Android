import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val configuredApiBaseUrl = providers.gradleProperty("BNBU_API_BASE_URL")
    .orElse(providers.environmentVariable("BNBU_API_BASE_URL"))
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

android {
    namespace = "edu.bnbu.student.mvp"
    compileSdk = 35

    defaultConfig {
        applicationId = "edu.bnbu.student.mvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"

    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField(
                "String",
                "BNBU_API_BASE_URL",
                (configuredApiBaseUrl ?: "http://123.207.5.70:3334/api").asBuildConfigString()
            )
        }
        release {
            isMinifyEnabled = true
            buildConfigField(
                "String",
                "BNBU_API_BASE_URL",
                // preReleaseBuild requires an explicit HTTPS value. The
                // placeholder only keeps IDE model/sync generation valid.
                (configuredApiBaseUrl ?: "https://configuration-required.invalid/api")
                    .asBuildConfigString()
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-video:3.0.4")

    // Networking & async (student backend integration)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

val validateReleaseApiBaseUrl by tasks.registering {
    group = "verification"
    description = "Requires an explicit HTTPS BNBU_API_BASE_URL for release builds."
    inputs.property("BNBU_API_BASE_URL", configuredApiBaseUrl ?: "")
    doLast {
        val value = configuredApiBaseUrl
            ?: throw GradleException(
                "Release builds require -PBNBU_API_BASE_URL=https://your-production-domain/api " +
                    "or the BNBU_API_BASE_URL environment variable."
            )
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri?.scheme?.equals("https", ignoreCase = true) != true || uri.host.isNullOrBlank()) {
            throw GradleException("Release BNBU_API_BASE_URL must be a valid HTTPS URL: $value")
        }
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            throw GradleException("Release BNBU_API_BASE_URL must not contain credentials, a query, or a fragment: $value")
        }
        if (!(uri.path ?: "").trimEnd('/').endsWith("/api")) {
            throw GradleException("Release BNBU_API_BASE_URL must end with /api: $value")
        }
        if (uri.host.equals("localhost", ignoreCase = true) || uri.host == "127.0.0.1" || uri.host == "10.0.2.2" || uri.host.endsWith(".invalid")) {
            throw GradleException("Release BNBU_API_BASE_URL must use the real production host: $value")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseApiBaseUrl)
    }
}
