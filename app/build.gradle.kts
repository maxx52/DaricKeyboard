import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

val klipyApiKey = (
    System.getenv("KLIPY_API_KEY")
        ?: localProperties.getProperty("KLIPY_API_KEY", "")
)

val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSetting(propertyName: String, environmentName: String): String? =
    System.getenv(environmentName)?.takeIf(String::isNotBlank)
        ?: keystoreProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)

val releaseStoreFile = releaseSetting("storeFile", "DARIC_STORE_FILE")
val releaseStorePassword = releaseSetting("storePassword", "DARIC_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("keyAlias", "DARIC_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("keyPassword", "DARIC_KEY_PASSWORD")
val releaseSigningAvailable = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "ru.maxx52.daric"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ru.maxx52.daric"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "klipy_api_key", klipyApiKey)
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val validateRuStoreRelease by tasks.registering {
    group = "verification"
    description = "Checks signing and API credentials required for a RuStore release"
    doLast {
        val missing = buildList {
            if (releaseStoreFile.isNullOrBlank()) add("storeFile / DARIC_STORE_FILE")
            if (releaseStorePassword.isNullOrBlank()) {
                add("storePassword / DARIC_STORE_PASSWORD")
            }
            if (releaseKeyAlias.isNullOrBlank()) add("keyAlias / DARIC_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("keyPassword / DARIC_KEY_PASSWORD")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured: " + missing.joinToString()
            )
        }
        if (klipyApiKey.isBlank()) {
            throw GradleException(
                "KLIPY_API_KEY is empty. A working production key is required for RuStore."
            )
        }
    }
}

tasks.matching {
    it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    dependsOn(validateRuStoreRelease)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation("com.google.ai.edge.litert:litert:1.4.2")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
