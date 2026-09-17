import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing, kept out of the repository: `keystore.properties` and the keystore it points at
 * are gitignored. A clone without them still builds - the release build just falls back to the
 * debug key, which is fine for running it yourself but produces an APK nobody else should install.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it).exists() } == true

/**
 * The version comes from the release workflow (`-PversionName=1.2 -PversionCode=5`), so the tag,
 * the APK and what the app reports about itself cannot drift apart - and versionCode, which Android
 * requires to increase for every update, is never a number anyone has to remember to bump.
 *
 * A local build falls back to something deliberately *below* anything published: a release APK you
 * built yourself should not be able to install over one from the releases page and pass itself off
 * as an update. Pass the two properties when you do want that.
 */
val appVersionName = (findProperty("versionName") as String?) ?: "dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.mati.shortformblocker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mati.shortformblocker"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A development build installs alongside the release one rather than colliding with
            // it. Without this both carry the applicationId `com.mati.shortformblocker` but are
            // signed with different keys - the debug key here, the release key in the published
            // APK - and Android refuses the second install with "App not installed as package
            // conflicts with an existing package", with no hint that signing is what it means.
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
