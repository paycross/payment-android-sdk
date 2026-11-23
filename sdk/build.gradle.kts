import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("maven-publish")
    id("signing")
    // Fails the build when the public ABI drifts from api/sdk.api.
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("org.jetbrains.dokka")
}

// Central verifies the pay-cross.com domain, so the groupId cannot be com.paycross.
group = "com.pay-cross"
version = providers.gradleProperty("paycrossVersion").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "com.paycross.sdk"
    compileSdk = 36

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
            // Central requires a javadoc jar to exist; its content is not checked.
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
    // Compose emits a public ComposableSingletons holder per file; list each by exact name so internal/ leaks still fail.
    ignoredClasses.addAll(
        listOf(
            "com.paycross.sdk.internal.ui.ComposableSingletons\$PaymentActivityKt",
            "com.paycross.sdk.internal.ui.components.ComposableSingletons\$CardInputFieldsKt",
            "com.paycross.sdk.internal.ui.components.ComposableSingletons\$SavedCardSelectorKt"
        )
    )
}

// Published as the API reference by reference.yml on each v* tag.
dokka {
    // Matches the Maven artifactId, not the Gradle project name.
    moduleName.set("paycross-android")

    dokkaSourceSets.configureEach {
        // Keeps internal packages out of the reference even if a declaration is made public.
        perPackageOption {
            matchingRegex.set("com\\.paycross\\.sdk\\.internal.*")
            suppress.set(true)
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            // Not "sdk": Central is a global namespace.
            artifactId = "paycross-android"
            afterEvaluate { from(components["release"]) }

            // Central requires these pom fields.
            pom {
                name.set("PayCross Android SDK")
                description.set("Card payments, 3-D Secure v2 and saved cards for Android.")
                url.set("https://github.com/paycross/payment-android-sdk")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
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

// Central rejects unsigned uploads; signing is skipped when either variable is unset.
// SIGNING_KEY is base64: CI secret handling mangles the armored block's newlines.
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
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // api, not implementation: their types appear in the public SDK signatures.
    api("androidx.activity:activity-compose:1.13.0")
    api("androidx.annotation:annotation:1.11.0")
    implementation("androidx.core:core-ktx:1.18.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")

    // Retrofit 2.11+: 2.9 predates OkHttp 5, which flutter_stripe pins and would drag the SDK onto.
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-gson:2.12.0")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.1")

    implementation("com.google.android.gms:play-services-wallet:20.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.12")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
