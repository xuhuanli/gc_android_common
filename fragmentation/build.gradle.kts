plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    namespace = "com.weikaiyun.fragmentation"
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.fragment:fragment-ktx:1.6.0")
    implementation("androidx.core:core-ktx:1.7.0")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
}
