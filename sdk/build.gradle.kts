import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("maven-publish")
    id("signing")
    // Pins the importable surface of the AAR: apiCheck fails the build whenever
    // the public ABI drifts from the committed api/sdk.api, so nothing under
    // internal/ can leak back into merchant-visible API unnoticed.
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

// com.pay-cross, not com.paycross: Maven Central verifies a namespace by reversing
// the domain EXACTLY, hyphens included, so pay-cross.com grants com.pay-cross and
// nothing else. paycross.com is a different company's domain, registered since
// 2010 and transfer-locked, so matching it is not an option. Hyphens are legal in
// a groupId and do not have to match the Kotlin package, which stays com.paycross.
//
// Central coordinates are permanent once published - a relocation POM is the only
// escape and it is lossy - so this is settled here rather than at first publish.
group = "com.pay-cross"
version = providers.gradleProperty("paycrossVersion").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "com.paycross.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
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

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            // Central requires a -javadoc artifact to be present. It checks that
            // the file exists, not what is in it, so this stays satisfiable even
            // if the SDK is later shipped closed-source.
            withJavadocJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

apiValidation {
    // The Compose compiler emits one public ComposableSingletons holder class
    // per file containing composable lambdas; its members are internal-mangled
    // and unusable from outside, but the class itself is public bytecode.
    // Listed by exact name rather than ignoring com.paycross.sdk.internal
    // wholesale, so a genuinely public type leaking from internal/ still fails
    // apiCheck - that leak is what this validation exists to catch. A new file
    // with composable lambdas will surface here; add its holder to this list.
    ignoredClasses.addAll(
        listOf(
            "com.paycross.sdk.internal.ui.ComposableSingletons\$PaymentActivityKt",
            "com.paycross.sdk.internal.ui.components.ComposableSingletons\$CardInputFieldsKt",
            "com.paycross.sdk.internal.ui.components.ComposableSingletons\$SavedCardSelectorKt"
        )
    )
}

publishing {
    publications {
        register<MavenPublication>("release") {
            // Not "sdk": Central is a global namespace and a merchant reading their
            // dependency block should be able to tell what they are depending on.
            // Follows com.stripe:stripe-android.
            artifactId = "paycross-android"
            afterEvaluate { from(components["release"]) }

            // Required by Central. Harmless on GitHub Packages, and cheaper to set
            // now than to discover missing at the first Portal upload.
            pom {
                name.set("PayCross Android SDK")
                description.set("Card payments, 3-D Secure v2 and saved cards for Android.")
                url.set("https://github.com/paycross/payment-android-sdk")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://github.com/paycross/payment-android-sdk/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("paycross")
                        name.set("PayCross")
                    }
                }
                scm {
                    url.set("https://github.com/paycross/payment-android-sdk")
                    connection.set("scm:git:https://github.com/paycross/payment-android-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/paycross/payment-android-sdk.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/paycross/payment-android-sdk")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}

// Central rejects unsigned uploads. The key arrives base64-wrapped because CI
// secret handling mangles the armored block's newlines. When either variable is
// absent, signing stays off entirely, so local builds and forks without the
// secrets still assemble and publish to GitHub Packages.
signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(
            String(Base64.getDecoder().decode(signingKey)),
            signingPassword
        )
        sign(publishing.publications)
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // api, not implementation: ActivityResultContract is the public supertype of
    // PayCrossContract and @ColorInt appears in PayCross.init's signature, so both
    // must be on the consumer's compile classpath.
    api("androidx.activity:activity-compose:1.8.2")
    api("androidx.annotation:annotation:1.7.1")
    implementation("androidx.core:core-ktx:1.19.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")

    // Networking
    // Retrofit 2.11+, not 2.9: 2.9 predates OkHttp 5, and Gradle resolves to the
    // highest version in the graph, so a merchant app that also ships
    // flutter_stripe (which pins OkHttp 5) drags this SDK onto it. Verified by
    // forcing okhttp 5.3.2 through the whole graph and rebuilding. Note the
    // Kotlin version above does the heavier lifting here - OkHttp 5 carries
    // Kotlin 2.2 metadata, which a 1.9 compiler cannot read at all.
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // Google Pay. The sheet, IsReadyToPay, the ActivityResult contract and the
    // official PayButton view all come from this one artifact. The Compose
    // wrapper (com.google.pay.button:compose-pay-button) is deliberately not
    // used: 1.1.0 drags androidx.core 1.15.0, whose AAR metadata demands
    // compileSdk 35 while this project pins 34.
    implementation("com.google.android.gms:play-services-wallet:20.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
