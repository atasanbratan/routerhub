import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

// Pure JVM on purpose: no Android, no OkHttp, no coroutines runtime. The smaller this contract is,
// the fewer things the host and an extension built months apart can disagree about.
kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Compile interface default bodies to real JVM default methods (no DefaultImpls classes).
        // That lets us add optional methods to RouterFirmware later without extensions compiled
        // against an older router-api failing with AbstractMethodError.
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    // `api` so that compileOnly consumers (extensions) also see stdlib types at compile time.
    api(libs.kotlin.stdlib)

    testImplementation(libs.junit)
}

// Extensions live in their own repository and compile against this module as
//   compileOnly("com.github.atasanbratan.routerhub:router-api:<tag>")
// JitPack builds it from a git tag (see jitpack.yml); `publishToMavenLocal` does the same locally.
publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            groupId = "com.github.atasanbratan.routerhub"
            artifactId = "router-api"
            version = providers.environmentVariable("VERSION").getOrElse("0.1.0-SNAPSHOT")
            pom {
                name.set("RouterHub router-api")
                description.set("The contract between the RouterHub Android app and its router extensions.")
                url.set("https://github.com/atasanbratan/routerhub")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm { url.set("https://github.com/atasanbratan/routerhub") }
            }
        }
    }
}
