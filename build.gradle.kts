import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyResolveDetails

buildscript {
    // Everything here is build-classpath tooling dragged in by AGP - none of it
    // ships in the AAR - but it is what the dependency graph is scanned against,
    // and AGP 8.13.2 still bundles versions with open advisories. Raised to the
    // listed release when something asks for an older one, never lowered; drop
    // entries as AGP catches up.
    fun patchedBuildDeps(): List<String> = listOf(
        "io.netty:netty-buffer:4.1.139.Final",
        "io.netty:netty-codec:4.1.139.Final",
        "io.netty:netty-codec-http:4.1.139.Final",
        "io.netty:netty-codec-http2:4.1.139.Final",
        "io.netty:netty-codec-socks:4.1.139.Final",
        "io.netty:netty-common:4.1.139.Final",
        "io.netty:netty-handler:4.1.139.Final",
        "io.netty:netty-handler-proxy:4.1.139.Final",
        "io.netty:netty-resolver:4.1.139.Final",
        "io.netty:netty-transport:4.1.139.Final",
        "io.netty:netty-transport-native-unix-common:4.1.139.Final",
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
    id("com.android.library") version "9.5.0" apply false
    // Kotlin 2.x is not optional. Any dependency compiled against Kotlin 2.x
    // metadata - OkHttp 5 among them - is unreadable to a 1.9 compiler, and the
    // Flutter plugin templates a merchant would consume this from are themselves
    // Kotlin 2.x. Staying on 1.9.21 makes the SDK uncombinable with both.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    // From Kotlin 2.0 the Compose compiler ships with Kotlin itself and is
    // applied as a plugin instead of pinned via composeOptions.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
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
