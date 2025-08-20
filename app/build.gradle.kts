plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gancao.gc_android_common"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gancao.gc_android_common"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.reflect)
    implementation(libs.fragmentKtx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(project(":fragmentation"))
    implementation(project(":pickerview"))
    implementation(project(":swiper_recyclerview"))
    implementation(project(":wheelview"))
    implementation(project(":labelview"))
    implementation(project(":guide"))
    implementation(project(":liveeventbus-x"))
    implementation(project(":bga-base-adapter"))
    implementation(project(":bga-photo-picker"))
    implementation(project(":mzbanner"))
    implementation(project(":sticky-headers-recyclerview"))
    implementation("io.github.lucksiege:pictureselector:v3.11.2")
    api(libs.glide)
    annotationProcessor(libs.glideCompiler)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
