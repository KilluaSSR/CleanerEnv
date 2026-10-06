plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "killua.dev.cleaner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "killua.dev.cleaner"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("../native/out/jniLibs")
            assets.directories.add("../native/out/assets")
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    jvmToolchain(11)
}

tasks.named("preBuild") {
    dependsOn(":buildNative")
}

dependencies {
    implementation(libs.arsenal.core)
    implementation(libs.arsenal.logging)
    implementation(libs.arsenal.spi)
    implementation(libs.arsenal.data)
    implementation(libs.arsenal.navigation)
    implementation(libs.arsenal.navigation.annotations)
    implementation(libs.arsenal.error)
    implementation(libs.arsenal.material)
    implementation(libs.arsenal.permission)
    implementation(libs.arsenal.update)
    implementation(libs.arsenal.network)
    ksp(libs.arsenal.navigation.processor)
    ksp(libs.arsenal.spi.processor)
    ksp(libs.arsenal.gate.processor)

    implementation(project(":core:ipc"))
    implementation(project(":server"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
