plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        renderscriptTargetApi = 30
        renderscriptSupportModeEnabled = true
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("proguard-rules.txt")
    }

    namespace = "fr.tvbarthel.lib.blurdialogfragment"

    lint {
        abortOnError = false
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.4.2")
}

