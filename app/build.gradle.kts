plugins {
    alias(libs.plugins.android.application)
}

// CI passes these from the git tag (e.g. -PversionName=1.2.3 -PversionCode=10203).
val appVersionName = providers.gradleProperty("versionName").getOrElse("1.0.0")
val appVersionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

// Release signing comes from the environment (GitHub secrets in CI); unsigned when absent.
val releaseKeystore = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull

android {
    namespace = "com.radagva.widgetkit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.radagva.widgetkit"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            // Optimize the whole app. Restricting R8 to library packages (packageScope) produced
            // an APK that crashed at startup with IllegalAccessError (kotlin.collections class
            // extended across an optimized/unoptimized boundary).
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}