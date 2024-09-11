plugins {
    id("com.android.library")
}

apply(from = "../maven_publish.gradle")

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("proguard-rules.txt")
    }

    resourcePrefix("x_recycler")
    namespace = "com.yanzhenjie.recyclerview.x"

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}