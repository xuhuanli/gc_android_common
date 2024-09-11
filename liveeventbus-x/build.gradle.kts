plugins {
    id("com.android.library")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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
}

dependencies {
    compileOnly("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    compileOnly("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    compileOnly("androidx.core:core-ktx:1.7.0")
}
