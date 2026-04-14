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
        // Xposed API
        maven { url = uri("https://raw.githubusercontent.com/rovo89/XposedBridge/master/maven/") }
    }
}

rootProject.name = "MiPayGPayLSP"
include(":app")
