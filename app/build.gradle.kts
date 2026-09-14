import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val fastTimers = providers.gradleProperty("fastTimers").orNull == "true"

// Release signing is read from keystore.properties (gitignored). CI writes that file from secrets.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "io.github.vanlh23.sharetextfile"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vanlh23.sharetextfile"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "1.0"

        val minute = 60_000L
        buildConfigField("long", "SAFETY_TTL_MS", "${if (fastTimers) 3 * minute else 60 * minute}L")
        buildConfigField("long", "GRACE_AFTER_CHOICE_MS", "${if (fastTimers) 1 * minute else 15 * minute}L")
        buildConfigField("long", "CANCEL_DELAY_MS", "${if (fastTimers) 20_000L else 1 * minute}L")
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
