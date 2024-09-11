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

    namespace = "com.bigkoo.pickerview"

    lint {
        abortOnError = false
    }
}

dependencies {
    api(project(path = ":wheelview"))
    implementation("androidx.annotation:annotation:1.6.0")
}
