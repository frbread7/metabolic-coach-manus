import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val releaseKeystorePath = providers.environmentVariable("MC_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("MC_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MC_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MC_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val debugKeystorePath = providers.environmentVariable("MC_DEBUG_KEYSTORE_PATH").orNull
val debugStorePassword = providers.environmentVariable("MC_DEBUG_STORE_PASSWORD").orNull
val debugKeyAlias = providers.environmentVariable("MC_DEBUG_KEY_ALIAS").orNull
val debugKeyPassword = providers.environmentVariable("MC_DEBUG_KEY_PASSWORD").orNull
val debugSigningConfigured = listOf(
    debugKeystorePath,
    debugStorePassword,
    debugKeyAlias,
    debugKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.young.metaboliccoach.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.young.metaboliccoach"
        minSdk = 30
        targetSdk = 36
        versionCode = 13
        versionName = "0.7.1"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseSigningConfig = if (releaseSigningConfigured) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(checkNotNull(releaseKeystorePath))
            storePassword = checkNotNull(releaseStorePassword)
            keyAlias = checkNotNull(releaseKeyAlias)
            keyPassword = checkNotNull(releaseKeyPassword)
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    } else {
        null
    }
    val debugSigningConfig = if (debugSigningConfigured) {
        signingConfigs.create("engineeringDebug") {
            storeFile = rootProject.file(checkNotNull(debugKeystorePath))
            storePassword = checkNotNull(debugStorePassword)
            keyAlias = checkNotNull(debugKeyAlias)
            keyPassword = checkNotNull(debugKeyPassword)
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    } else {
        null
    }

    buildTypes {
        debug {
            if (debugSigningConfig != null) {
                signingConfig = debugSigningConfig
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = releaseSigningConfig
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.watchface.complications.data.source)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    implementation(libs.google.play.services.wearable)
    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)

    testImplementation(libs.junit)
}
