pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MetabolicCoach"

include(
    ":core:model",
    ":core:domain",
    ":core:data",
    ":core:sync",
    ":phone",
    ":wear",
    ":watchface",
)

