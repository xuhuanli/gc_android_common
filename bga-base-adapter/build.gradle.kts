plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    namespace = "cn.bingoogolapple.baseadapter"

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
    implementation("androidx.core:core-ktx:1.7.0")

    // noinspection GradleCompatible
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-common:2.3.1")
}
