import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreBase64 = System.getenv("SIGNING_KEY_BASE64")
val keystoreStorePass = System.getenv("SIGNING_STORE_PASSWORD")
val keystoreKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val keystoreKeyPass = System.getenv("SIGNING_KEY_PASSWORD")
val hasReleaseKeystore = !keystoreBase64.isNullOrBlank() &&
    !keystoreStorePass.isNullOrBlank() &&
    !keystoreKeyAlias.isNullOrBlank() &&
    !keystoreKeyPass.isNullOrBlank()

android {
    namespace = "com.solgaming.solstreamstudio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.solgaming.solstreamstudio"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val ksFile = rootProject.layout.buildDirectory.file("signing/release.jks").get().asFile
                ksFile.parentFile.mkdirs()
                ksFile.writeBytes(Base64.getMimeDecoder().decode(keystoreBase64))
                storeFile = ksFile
                storePassword = keystoreStorePass
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
