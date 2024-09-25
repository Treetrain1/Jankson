plugins {
    java
    eclipse
    idea
    `maven-publish`
    id("fabric-loom") version("+")
}

group = "blue.endless"
base.archivesName = "Jankson"
version = "1.2.3"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
}

loom {
    runtimeOnlyLog4j = true
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    testImplementation("net.fabricmc:fabric-loader:0.16.5")
    testImplementation("net.fabricmc:fabric-loader-junit:0.16.5")

    testImplementation("junit:junit:4.13.2")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "jankson"
        )
    }
}

tasks {

    register("javadocJar", Jar::class) {
        dependsOn(classes)
        archiveClassifier = "javadoc"
        from(javadoc.get().destinationDir)
    }
}

val sourcesJar: Task by tasks
val javadocJar: Task by tasks

var versionSuffix = ""
if (System.getenv().containsKey("BUILD_NUMBER")) {
    versionSuffix = "-${System.getenv()["BUILD_NUMBER"]!!}";
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
    options.release = 21
    options.isFork = true
    options.isIncremental = true
}

artifacts {
    archives(sourcesJar)
    archives(javadocJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifact(javadocJar)

            pom {
                groupId = project.group.toString()
                artifactId = rootProject.base.archivesName.get().lowercase()
                version = project.version.toString()
            }
        }
    }
}

if (file("private.gradle").exists()) {
    apply(from = "private.gradle")
}

//defaultTasks = mutableListOf("clean", "build", "sourcesJar");