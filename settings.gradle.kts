pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

// Uploads the existing maven-publish publications through the Central Portal
// publisher API - the Portal has no plain Maven endpoint maven-publish could
// push to. AUTOMATIC: a tag that passes validation publishes without a Portal
// click. Central artifacts are permanent, so a bad release is fixed by the
// next version, never by unpublishing.
nmcpSettings {
    centralPortal {
        username = System.getenv("CENTRAL_TOKEN_USERNAME")
        password = System.getenv("CENTRAL_TOKEN_PASSWORD")
        publishingType = "AUTOMATIC"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "paycross-android-sdk"
include(":sdk")
include(":demo-app")
