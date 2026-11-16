import java.io.File
import java.util.Properties

// Credentials for the tribalfs/oneui-design GitHub Packages repo. Local
// credentials live outside the checkout under ~/.config/twidget. CI falls
// back to environment variables.
val githubPropertiesFile = File(
    System.getProperty("user.home"),
    ".config/twidget/github.properties",
)
val githubProperties = Properties().apply {
    githubPropertiesFile.takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val ghPackagesUser: String =
    githubProperties.getProperty("ghUsername") ?: System.getenv("GH_PACKAGES_USER") ?: ""
val ghPackagesToken: String =
    githubProperties.getProperty("ghAccessToken") ?: System.getenv("GH_PACKAGES_TOKEN") ?: ""

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// OneUI Design and SESL artifacts are hosted in tribalfs GitHub Packages.
// The classic PAT above needs read:packages access to these repositories.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/tribalfs/oneui-design") {
            credentials { username = ghPackagesUser; password = ghPackagesToken }
            content { includeGroup("io.github.tribalfs") }
        }
        maven("https://maven.pkg.github.com/tribalfs/sesl-androidx") {
            credentials { username = ghPackagesUser; password = ghPackagesToken }
            content { includeGroupByRegex("sesl\\.androidx\\..*") }
        }
        maven("https://maven.pkg.github.com/tribalfs/sesl-material-components-android") {
            credentials { username = ghPackagesUser; password = ghPackagesToken }
            content { includeGroup("sesl.com.google.android.material") }
        }
    }
}

rootProject.name = "OneOS"

include(":app")
// OneOS inside the guest: "Back to One UI" (guest/build.gradle.kts).
include(":guest")
include(":core:model")
include(":core:device")
include(":core:root")
include(":core:safety")
