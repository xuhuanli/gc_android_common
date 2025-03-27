plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    namespace = "com.timehop.stickyheadersrecyclerview"
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
