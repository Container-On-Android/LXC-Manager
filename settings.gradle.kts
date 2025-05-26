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
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
}

rootProject.name = "LXC Manager"
include(
    ":app",
    ":ReTerminal:application",
    ":ReTerminal:components",
    ":ReTerminal:main",
    ":ReTerminal:resources",
    ":ReTerminal:rish"
)
