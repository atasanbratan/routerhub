import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val officialSigners: String = providers.gradleProperty("routerhub.officialSigners").getOrElse("")

android {
    namespace = "dev.routerhub.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.routerhub"
        minSdk = 28 // PackageInfo.signingInfo / GET_SIGNING_CERTIFICATES
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "OFFICIAL_EXTENSION_SIGNERS", "\"$officialSigners\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // The "platform" handed to extensions at runtime: router-api, Kotlin stdlib, coroutines and
    // OkHttp. Extensions compile against these as compileOnly and link to the host's copies through
    // ExtensionClassLoader.SHARED_PACKAGES, so their versions are part of the extension contract.
    implementation(project(":router-api"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
}
