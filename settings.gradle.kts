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
// push to. USER_MANAGED holds the upload for a human "Publish" click in the
// Portal; flip to AUTOMATIC once a release has gone out clean, since Central
// artifacts are permanent and cannot be unpublished.
nmcpSettings {
    centralPortal {
        username = System.getenv("CENTRAL_TOKEN_USERNAME")
        password = System.getenv("CENTRAL_TOKEN_PASSWORD")
        publishingType = "USER_MANAGED"
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
