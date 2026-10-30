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

rootProject.name = "kwachatrack"

include(":app")
include(":core:domain", ":core:parser", ":core:data", ":core:ui")
include(":feature:onboarding", ":feature:dashboard", ":feature:transactions", ":feature:insights", ":feature:settings")
