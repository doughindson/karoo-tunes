import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val spotifyClientId: String = System.getenv("SPOTIFY_CLIENT_ID")
    ?: providers.gradleProperty("SPOTIFY_CLIENT_ID").orNull
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }.getProperty("SPOTIFY_CLIENT_ID")
    }
    ?: throw GradleException(
        "SPOTIFY_CLIENT_ID is not set. Add it to local.properties, gradle.properties or the environment."
    )

android {
    namespace = "com.doughindson.karootunes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.doughindson.karootunes"
        minSdk = 26
        // The Karoo 3 runs Android 12 (API 31). Targeting a newer API only opts the app into
        // behaviour changes for newer OS versions it never runs on, so stay at the device level.
        //noinspection ExpiredTargetSdkVersion,OldTargetApi
        targetSdk = 31
        versionCode = 100 + (System.getenv("BUILD_NUMBER")?.toInt() ?: 1)
        versionName = System.getenv("RELEASE_VERSION") ?: "1.0"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")

    }

    // Only CI has the release keystore. Locally, release builds come out unsigned.
    val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
    if (!keystoreBase64.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                val keystoreFile = File.createTempFile("keystore", ".jks").apply { deleteOnExit() }
                keystoreFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/** Release notes for manifest.json come from the first section of CHANGELOG.md. */
fun latestReleaseNotes(): String {
    val changelog = rootProject.file("CHANGELOG.md")
    if (!changelog.isFile) return ""
    return changelog.readLines()
        .dropWhile { !it.startsWith("## ") }
        .drop(1)
        .takeWhile { !it.startsWith("## ") }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    val versionName = android.defaultConfig.versionName
    val versionCode = android.defaultConfig.versionCode
    val releaseNotes = latestReleaseNotes()
    val manifestFile = file("$projectDir/manifest.json")
    val releaseUrl = "https://github.com/doughindson/karoo-tunes/releases/latest/download"

    doLast {
        val manifest = mapOf(
            "label" to "Karoo Tunes",
            "packageName" to "com.doughindson.karootunes",
            "iconUrl" to "$releaseUrl/karoo-tunes.png",
            "latestApkUrl" to "$releaseUrl/app-release.apk",
            "latestVersion" to versionName,
            "latestVersionCode" to versionCode,
            "developer" to "doughindson",
            "description" to "Provides media controls for Spotify. Can be used as a remote control for Spotify running on your phone / computer or offline if you have sideloaded the Spotify app itself on the Karoo. Currently not compatible with Karoo 2.",
            "releaseNotes" to releaseNotes,
            "screenshotUrls" to listOf("player.png", "playlists.png", "podcast.png", "login.png").map { "$releaseUrl/$it" },
            "tags" to listOf("entertainment")
        )

        manifestFile.writeText(groovy.json.JsonBuilder(manifest).toPrettyString())
        println("Generated manifest.json with version $versionName ($versionCode)")
    }
}

tasks.named("assemble") {
    dependsOn("generateManifest")
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.gson)

    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.bundles.glance)

    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.common)
}
