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

    namespace = "com.yqritc.recyclerviewflexibledivider"

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

