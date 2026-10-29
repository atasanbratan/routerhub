import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}

val officialSigners: String = providers.gradleProperty("routerhub.officialSigners").getOrElse("")

// The release key, handed over by CI (.github/workflows/release.yml) as environment variables. Without
// them a release build is left unsigned, so a local `assembleRelease` still works for testing R8.
val releaseKeyStore: String? = providers.environmentVariable("ROUTERHUB_KEYSTORE").orNull

// The Play build compiles the official extensions in, because Google Play allows neither the
// all-packages permission extension discovery needs nor loading code from APKs installed elsewhere.
// It reads their sources from a checkout of routerhub-extensions, by default next to this repository.
val extensionsCheckout: File =
    rootProject.file(providers.gradleProperty("routerhub.extensions").getOrElse("../routerhub-extensions"))
val bundledExtensions = listOf("demo", "openwrt-ssh", "tenda-ac", "tplink-adsl")

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
        // Where the About screen looks for updates and links to; forks change it here.
        buildConfigField("String", "GITHUB_REPO", "\"atasanbratan/routerhub\"")
        buildConfigField("String", "WEBSITE_URL", "\"https://atasanbratan.github.io/routerhub\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // GitHub releases and the website: extensions are separate APKs, updates come from GitHub.
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "INSTALLABLE_EXTENSIONS", "true")
            buildConfigField("boolean", "UPDATE_CHECKER", "true")
        }
        // Google Play: the official extensions are built in, and Play delivers updates.
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "INSTALLABLE_EXTENSIONS", "false")
            buildConfigField("boolean", "UPDATE_CHECKER", "false")
        }
    }

    sourceSets.getByName("play") {
        kotlin.srcDirs(bundledExtensions.map { extensionsCheckout.resolve("extensions/$it/src/main/kotlin") })
    }

    signingConfigs {
        if (releaseKeyStore != null) {
            create("release") {
                storeFile = file(releaseKeyStore)
                storePassword = providers.environmentVariable("ROUTERHUB_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ROUTERHUB_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ROUTERHUB_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.aboutlibraries.compose.m3)

    // What the bundled extensions use besides the platform above.
    "playImplementation"(libs.jsoup)
    "playImplementation"(libs.jsch)

    testImplementation(libs.junit)
}

// Fail early, with the fix, instead of with hundreds of unresolved references.
tasks.matching { it.name.startsWith("compilePlay") }.configureEach {
    doFirst {
        val missing = bundledExtensions.filterNot { extensionsCheckout.resolve("extensions/$it").isDirectory }
        check(missing.isEmpty()) {
            "The Play build compiles in extensions from $extensionsCheckout, which lacks $missing. Clone " +
                "https://github.com/atasanbratan/routerhub-extensions there or pass -Prouterhub.extensions=<path>."
        }
    }
}
