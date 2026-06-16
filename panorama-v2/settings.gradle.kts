pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://androidsdk.insta360.com/repository/maven-public/") }
    }
}
rootProject.name = "panorama-v2"
include(":core", ":android", ":app")
