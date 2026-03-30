buildCache {
    local {
        isEnabled = true
    }
    remote<HttpBuildCache> {
        url = uri("http://localhost:5071/cache/")
        // Always enabled — `make build` ensures the container is running first.
        // If the server is unreachable Gradle warns and falls back to local cache.
        isEnabled = true
        isPush = true
    }
}

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
    }
}

rootProject.name = "Bank SMS Tracker"
include(":app")
