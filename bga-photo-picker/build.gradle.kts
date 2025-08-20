plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    namespace = "cn.bingoogolapple.photopicker"

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
    implementation(project(":bga-base-adapter"))
    implementation("androidx.appcompat:appcompat:1.4.2")
    compileOnly("androidx.legacy:legacy-support-v4:1.0.0")
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
    compileOnly("com.github.bumptech.glide:glide:4.10.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.10.0")
}
