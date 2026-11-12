pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradleup.nmcp.settings") version "1.6.2"
}

// Central Portal publishing: AUTOMATIC releases a validated tag at once, and Central artifacts cannot be unpublished.
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
