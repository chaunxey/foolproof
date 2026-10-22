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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 穿山甲官方 Maven（只提供 com.pangle.cn 的广告 SDK）
        maven {
            url = uri("https://artifact.bytedance.com/repository/pangle")
            content { includeGroup("com.pangle.cn") }
        }
    }
}

rootProject.name = "Foolproof"
include(":app")
include(":base")
include(":common")
include(":ads:api")
include(":ads:meta")
include(":ads:pangle")
include(":features:image-compression")
include(":features:duplicate-cleanup")
include(":features:video-compression")
include(":features:file-conversion")
