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

    namespace = "com.app.hubert.guide"
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.fragment:fragment:1.3.6")
}
