import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyResolveDetails

buildscript {
    // Build-classpath pins that clear advisories in AGP 8.13.2's bundled versions; never lower them.
    fun patchedBuildDeps(): List<String> = listOf(
        "io.netty:netty-buffer:4.1.140.Final",
        "io.netty:netty-codec:4.1.140.Final",
        "io.netty:netty-codec-http:4.1.140.Final",
        "io.netty:netty-codec-http2:4.1.140.Final",
        "io.netty:netty-codec-socks:4.1.140.Final",
        "io.netty:netty-common:4.1.140.Final",
        "io.netty:netty-handler:4.1.140.Final",
        "io.netty:netty-handler-proxy:4.1.140.Final",
        "io.netty:netty-resolver:4.1.140.Final",
        "io.netty:netty-transport:4.1.140.Final",
        "io.netty:netty-transport-native-unix-common:4.1.140.Final",
        "org.bouncycastle:bcprov-jdk18on:1.86",
        "org.bouncycastle:bcpkix-jdk18on:1.86",
        "org.bouncycastle:bcutil-jdk18on:1.86",
        "org.bouncycastle:bcpg-jdk18on:1.86",
        "org.apache.commons:commons-compress:1.28.0",
        "commons-io:commons-io:2.16.1",
        "org.jdom:jdom2:2.0.6.1",
        "org.bitbucket.b_c:jose4j:0.9.7",
        "org.freemarker:freemarker:2.3.35",
        "org.jsoup:jsoup:1.23.2",
        "com.google.protobuf:protobuf-java:3.25.9",
        "com.google.protobuf:protobuf-java-util:3.25.9",
        "com.google.protobuf:protobuf-kotlin:3.25.9",
        "com.google.guava:guava:33.7.2-jre",
        "com.fasterxml.jackson.core:jackson-annotations:2.18.12",
        "com.fasterxml.jackson.core:jackson-core:2.18.12",
        "com.fasterxml.jackson.core:jackson-databind:2.18.12",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.12",
        "com.fasterxml.jackson:jackson-bom:2.18.12",
        "com.fasterxml.jackson.module:jackson-module-kotlin:2.18.12",
    )

    fun isOlder(version: String, floor: String): Boolean {
        val a = version.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val b = floor.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    fun patchedVersionRule(): Action<DependencyResolveDetails> = object : Action<DependencyResolveDetails> {
        override fun execute(details: DependencyResolveDetails) {
            val listed = patchedBuildDeps().firstOrNull {
                it.substringBeforeLast(':') == "${details.requested.group}:${details.requested.name}"
            }
            val requested = details.requested.version?.takeIf { it.isNotEmpty() } ?: return
            if (listed != null) {
                val target = listed.substringAfterLast(':')
                if (isOlder(requested, target)) {
                    details.useVersion(target)
                }
            }
        }
    }

    configurations.classpath {
        resolutionStrategy.eachDependency(patchedVersionRule())
    }
    project.extra["patchedVersionRule"] = patchedVersionRule()
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    // Stays on Kotlin 2.x: OkHttp 5 and the Flutter templates use it, and a 1.9 compiler cannot read them.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    // Kotlin 2.x applies the Compose compiler as a plugin; composeOptions does not apply.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.2" apply false
    id("org.jetbrains.dokka") version "2.3.0" apply false
}

val patchedVersionRule = rootProject.extra["patchedVersionRule"] as Action<DependencyResolveDetails>

allprojects {
    buildscript {
        configurations.all {
            resolutionStrategy.eachDependency(patchedVersionRule)
        }
    }
    configurations.configureEach {
        resolutionStrategy.eachDependency(patchedVersionRule)
    }
}
