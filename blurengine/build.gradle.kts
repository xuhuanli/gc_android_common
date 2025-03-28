plugins {
    id("com.android.library")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        renderscriptTargetApi = 30
        renderscriptSupportModeEnabled = true
        minSdk = 21
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

