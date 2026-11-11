pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JianxinIME"
include(":app", ":keyboard", ":settings", ":engine-rime", ":speech", ":core")
include(":work-profile-helper")
include(":profile-control", ":sms-code")
