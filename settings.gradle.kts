import java.io.File
import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        mavenLocal()
        maven {
            val localProps = Properties().apply {
                load(File(rootDir, "local.properties").inputStream())
            }
            url = uri("http://nexus.arashivision.com:9999/repository/maven-releases/")
            isAllowInsecureProtocol = true
            credentials {
                username = localProps.getProperty("nexus.username")
                password = localProps.getProperty("nexus.password")
            }
        }
    }
}

rootProject.name = "sdkdemo2"
include(":app")

include(":core:math")
include(":core:sensor-fusion")
include(":core:detection")
include(":core:vr")

include(":data:camera")
include(":data:media")
include(":data:sensor")

include(":domain")

include(":feature:capture")
include(":feature:player")
include(":feature:connect")
include(":feature:shot")
include(":feature:settings")
