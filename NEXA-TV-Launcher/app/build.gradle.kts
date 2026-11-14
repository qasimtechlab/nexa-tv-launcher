plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is supplied only by the protected GitHub Actions secrets.
// Never commit the keystore or passwords to this repository.
val releaseKeystorePath = System.getenv("NEXA_RELEASE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("NEXA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("NEXA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("NEXA_RELEASE_KEY_PASSWORD")

val releaseSigningReady = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.qasim.nexa.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qasim.nexa.launcher"
        minSdk = 21
        targetSdk = 35
        versionCode = 13
        versionName = "1.3.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
