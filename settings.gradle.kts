pluginManagement {
    repositories {
        mavenCentral()
        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven("https://maven.fabricmc.net")
        gradlePluginPortal()
    }
}