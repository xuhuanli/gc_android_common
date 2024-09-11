plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":bga-base-adapter"))
    implementation("androidx.appcompat:appcompat:1.4.2")
    compileOnly("androidx.legacy:legacy-support-v4:1.0.0")
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
    compileOnly("com.github.bumptech.glide:glide:4.10.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.10.0")
}
