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

android {
    namespace = "com.young.metaboliccoach.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.young.metaboliccoach.watchface"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    buildTypes {
        debug {
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
