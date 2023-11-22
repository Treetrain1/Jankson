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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

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
    minecraft("com.mojang:minecraft:1.20.2")
    mappings(loom.officialMojangMappings())
    testImplementation("net.fabricmc:fabric-loader:0.14.24")
    testImplementation("net.fabricmc:fabric-loader-junit:0.14.24")

    testImplementation("junit:junit:4.13.1")

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
    options.release = 17
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