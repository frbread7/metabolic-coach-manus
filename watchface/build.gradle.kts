plugins {
    alias(libs.plugins.android.application)
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
    namespace = "com.young.metaboliccoach.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.young.metaboliccoach.watchface"
        minSdk = 36
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.2"
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
            // WFF packages are resource-only. Minification removes generated-but-unused R code.
            isDebuggable = false
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = releaseSigningConfig
        }
    }
}
