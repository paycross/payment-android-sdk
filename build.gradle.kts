plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    // Kotlin 2.x is not optional. Any dependency compiled against Kotlin 2.x
    // metadata - OkHttp 5 among them - is unreadable to a 1.9 compiler, and the
    // Flutter plugin templates a merchant would consume this from are themselves
    // Kotlin 2.x. Staying on 1.9.21 makes the SDK uncombinable with both.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    // From Kotlin 2.0 the Compose compiler ships with Kotlin itself and is
    // applied as a plugin instead of pinned via composeOptions.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

// Everything here is build-classpath tooling dragged in by AGP - none of it
// ships in the AAR - but it is what the dependency graph is scanned against,
// and AGP 8.13.2 still bundles versions with open advisories. Forced to the
// first patched release of each; drop entries as AGP catches up.
val patchedBuildDeps = listOf(
    "io.netty:netty-buffer:4.1.137.Final",
    "io.netty:netty-codec:4.1.137.Final",
    "io.netty:netty-codec-http:4.1.137.Final",
    "io.netty:netty-codec-http2:4.1.137.Final",
    "io.netty:netty-codec-socks:4.1.137.Final",
    "io.netty:netty-common:4.1.137.Final",
    "io.netty:netty-handler:4.1.137.Final",
    "io.netty:netty-handler-proxy:4.1.137.Final",
    "io.netty:netty-resolver:4.1.137.Final",
    "io.netty:netty-transport:4.1.137.Final",
    "io.netty:netty-transport-native-unix-common:4.1.137.Final",
    "org.bouncycastle:bcprov-jdk18on:1.84",
    "org.bouncycastle:bcpkix-jdk18on:1.84",
    "org.bouncycastle:bcutil-jdk18on:1.84",
    "org.apache.commons:commons-compress:1.26.0",
    "commons-io:commons-io:2.16.1",
    "org.jdom:jdom2:2.0.6.1",
    "org.bitbucket.b_c:jose4j:0.9.6",
    "com.google.protobuf:protobuf-java:3.25.5",
    "com.google.protobuf:protobuf-java-util:3.25.5",
)

allprojects {
    buildscript {
        configurations.all {
            resolutionStrategy.force(patchedBuildDeps)
        }
    }
}
