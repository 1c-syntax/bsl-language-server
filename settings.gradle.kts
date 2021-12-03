rootProject.name = "bsl-language-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
        mavenLocal()
    }
}
include("root")
