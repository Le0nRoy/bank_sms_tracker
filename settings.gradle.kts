buildCache {
    local {
        isEnabled = true
    }
    remote<HttpBuildCache> {
        url = uri("http://localhost:5071/cache/")
        // Enable with: ./gradlew <task> -PbuildCacheRemote=true
        // Requires gradle-cache service: docker compose up -d gradle-cache
        isEnabled = providers.gradleProperty("buildCacheRemote").map { it.toBoolean() }.getOrElse(false)
        isPush = providers.gradleProperty("buildCacheRemote").map { it.toBoolean() }.getOrElse(false)
        isAllowUntrustedServer = false
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
    }
}

rootProject.name = "Bank SMS Tracker"
include(":app")
