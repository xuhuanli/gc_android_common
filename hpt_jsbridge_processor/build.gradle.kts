plugins {
    id("org.jetbrains.kotlin.jvm")
}

apply(from = "../maven_publish_jvm.gradle")

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.27")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("com.squareup:kotlinpoet-ksp:2.1.0")
}

kotlin {
    jvmToolchain(17)
}
