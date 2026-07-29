import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// google-services.json identifies the Firebase project; it is intentionally
// supplied by the release environment, never generated or substituted here.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val configuredApiBaseUrl = providers.gradleProperty("BNBU_API_BASE_URL")
    .orElse(providers.environmentVariable("BNBU_API_BASE_URL"))
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

// Release signing material is deliberately external to source control.  CI must
// supply these values as environment variables; a locally ignored
// keystore.properties file is supported for a developer's protected machine.
val localSigningProperties = Properties()
val localSigningPropertiesFile = rootProject.file("keystore.properties")
if (localSigningPropertiesFile.isFile) {
    localSigningPropertiesFile.inputStream().use(localSigningProperties::load)
}

fun releaseSigningValue(name: String): String? = providers.environmentVariable(name)
    .orElse(providers.gradleProperty(name))
    .orElse(providers.provider { localSigningProperties.getProperty(name) })
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val releaseStoreFileValue = releaseSigningValue("BNBU_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("BNBU_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("BNBU_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("BNBU_RELEASE_KEY_PASSWORD")
val releaseStoreFile = releaseStoreFileValue?.let(rootProject::file)
val missingReleaseSigningValues = listOf(
    "BNBU_RELEASE_STORE_FILE" to releaseStoreFileValue,
    "BNBU_RELEASE_STORE_PASSWORD" to releaseStorePassword,
    "BNBU_RELEASE_KEY_ALIAS" to releaseKeyAlias,
    "BNBU_RELEASE_KEY_PASSWORD" to releaseKeyPassword
).filter { (_, value) -> value == null }.map { (name, _) -> name }

android {
    namespace = "edu.bnbu.student.mvp"
    compileSdk = 35

    defaultConfig {
        applicationId = "edu.bnbu.student.mvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PRIVACY_POLICY_VERSION", "\"2.1\"")

    }

    signingConfigs {
        create("release") {
            // Do not set empty values: preReleaseBuild reports a clear error
            // through validateReleaseSigningConfiguration below.
            if (missingReleaseSigningValues.isEmpty()) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
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
            signingConfig = signingConfigs.getByName("release")
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
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Firebase BoM keeps Google Play services / FCM artifacts mutually compatible.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Networking & async (student backend integration)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-android")
    // Android 17 removed the reflected InputManager.getInstance() path used
    // by older Espresso releases. 3.7.0 uses the supported system-service API.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

/**
 * Locale must come from AppLanguagePreferences, never directly from the
 * device. Keeping this as a build-time boundary prevents a newly added page,
 * dialog, service, or formatter from reintroducing the cold-start mismatch.
 */
val verifyAppLocaleBoundary by tasks.registering {
    group = "verification"
    description = "Rejects direct system-locale reads in production Kotlin sources."
    val sources = fileTree("src/main/java") { include("**/*.kt") }
    inputs.files(sources)

    doLast {
        val forbidden = listOf(
            Regex("\\b(?:java\\.util\\.)?Locale\\.getDefault\\(\\)"),
            Regex("\\bResources\\.getSystem\\(\\)"),
            Regex("\\bLocaleList\\.getDefault\\(\\)")
        )
        val violations = sources.files.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.any { expression -> expression.containsMatchIn(line) }) {
                    "${source.relativeTo(projectDir)}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }
        check(violations.isEmpty()) {
            "Use AppLanguagePreferences.currentLocale or localizedContext instead of " +
                "reading the device locale directly:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyAppLocaleBoundary)
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

val validateReleaseFirebaseConfiguration by tasks.registering {
    group = "verification"
    description = "Requires app/google-services.json for release builds with FCM enabled."
    doLast {
        check(file("google-services.json").isFile) {
            "Release builds require app/google-services.json from the configured Firebase project."
        }
    }
}

val validateReleaseSigningConfiguration by tasks.registering {
    group = "verification"
    description = "Requires external signing material for release builds."
    doLast {
        check(missingReleaseSigningValues.isEmpty()) {
            "Release builds require signing configuration. Set " +
                missingReleaseSigningValues.joinToString() +
                " as CI environment variables or in the ignored keystore.properties file. " +
                "See keystore.properties.example."
        }
        check(releaseStoreFile?.isFile == true) {
            "BNBU_RELEASE_STORE_FILE must point to an existing keystore file: " +
                (releaseStoreFileValue ?: "<not set>")
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseApiBaseUrl)
        dependsOn(validateReleaseFirebaseConfiguration)
        dependsOn(validateReleaseSigningConfiguration)
    }
}
