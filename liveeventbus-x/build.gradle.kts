plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }
    namespace = "com.jeremyliao.liveeventbus"
}

dependencies {
    compileOnly("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    compileOnly("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    compileOnly("androidx.core:core-ktx:1.7.0")
}
