import java.io.File
import java.util.Base64
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreBase64 = System.getenv("SIGNING_KEY_BASE64")?.trim()
val keystoreStorePass = System.getenv("SIGNING_STORE_PASSWORD")?.trim()
val keystoreKeyAlias = System.getenv("SIGNING_KEY_ALIAS")?.trim()
val keystoreKeyPass = System.getenv("SIGNING_KEY_PASSWORD")?.trim()

val hasReleaseKeystore = !keystoreBase64.isNullOrBlank() &&
    !keystoreStorePass.isNullOrBlank() &&
    !keystoreKeyAlias.isNullOrBlank() &&
    !keystoreKeyPass.isNullOrBlank()

gradle.taskGraph.whenReady {
    val requestsReleaseApk = allTasks.any { task ->
        task.name.contains("Release") &&
            (task.name.startsWith("assemble") ||
                task.name.startsWith("bundle") ||
                task.name.startsWith("package"))
    }
    if (requestsReleaseApk && !hasReleaseKeystore) {
        throw GradleException(
            "Release signing credentials are missing! Set SIGNING_KEY_BASE64, " +
                "SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, and SIGNING_KEY_PASSWORD in GitHub Secrets."
        )
    }
}

android {
    namespace = "com.solgaming.solstreamstudio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.solgaming.solstreamstudio"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val ksFile = File(
                    System.getProperty("java.io.tmpdir"),
                    "solstream-release-signing.jks"
                )
                ksFile.parentFile?.mkdirs()
                ksFile.writeBytes(Base64.getMimeDecoder().decode(keystoreBase64))
                storeFile = ksFile
                storePassword = keystoreStorePass
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
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
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = false
        warningsAsErrors = false
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
    testImplementation("junit:junit:4.13.2")
}
