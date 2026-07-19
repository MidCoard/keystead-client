pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "keystead-client"

includeBuild("../keystead") {
    dependencySubstitution {
        substitute(module("top.focess:keystead-core")).using(project(":keystead-core"))
    }
}
