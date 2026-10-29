pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "routerhub"

include(":router-api")

// JitPack publishes :router-api for the extensions repository and has no Android SDK, so it only
// gets the pure-JVM module. Everywhere else the app is part of the build.
if (System.getenv("JITPACK") == null) {
    include(":app")
}
