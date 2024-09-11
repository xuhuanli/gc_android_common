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

    namespace = "com.contrarywind.view"

    lint {
        abortOnError = false
    }
}

dependencies {
    // compile(fileTree(include = listOf("*.jar"), dir = "libs"))
    implementation("androidx.annotation:annotation:1.6.0")
}