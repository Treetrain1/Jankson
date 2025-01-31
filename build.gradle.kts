import groovy.xml.XmlSlurper
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import java.io.FileNotFoundException
import java.net.URI

plugins {
    java
    eclipse
    idea
    `maven-publish`
    id("fabric-loom") version("1.10.+")
    id("org.ajoberstar.grgit") version("+")
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
    testImplementation("net.fabricmc:fabric-loader:0.16.9")
    testImplementation("net.fabricmc:fabric-loader-junit:0.16.9")

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

val env: MutableMap<String, String> = System.getenv()

publishing {
    val mavenUrl = env["MAVEN_URL"]
    val mavenUsername = env["MAVEN_USERNAME"]
    val mavenPassword = env["MAVEN_PASSWORD"]

    //val release = mavenUrl?.contains("release")
    val snapshot = mavenUrl?.contains("snapshot")

    val publishingValid = rootProject == project && !mavenUrl.isNullOrEmpty() && !mavenUsername.isNullOrEmpty() && !mavenPassword.isNullOrEmpty()

    val publishVersion = project.version.toString()//makeModrinthVersion(mod_version)
    val snapshotPublishVersion = "$publishVersion-SNAPSHOT" //publishVersion + if (snapshot == true) "-SNAPSHOT" else ""

    val publishGroup = project.group.toString()
    val artifact = rootProject.base.archivesName.get().lowercase()

    val hash = if (grgit.branch != null && grgit.branch.current() != null) grgit.branch.current().fullName else ""

    publications {
        var publish = true
        try {
            if (publishingValid) {
                try {
                    val xml = ResourceGroovyMethods.getText(
                        URI.create("$mavenUrl/${publishGroup.replace('.', '/')}/$snapshotPublishVersion/$publishVersion.pom").toURL()
                    )
                    val metadata = XmlSlurper().parseText(xml)

                    if (metadata.getProperty("hash").equals(hash)) {
                        publish = false
                    }
                } catch (ignored: FileNotFoundException) {
                    // No existing version was published, so we can publish
                }
            } else {
                publish = false
            }
        } catch (e: Exception) {
            publish = false
            println("Unable to publish to maven. The maven server may be offline.")
        }

        if (publish) {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                artifact(javadocJar)

                pom {
                    groupId = publishGroup
                    artifactId = artifact
                    version = snapshotPublishVersion
                    withXml {
                        asNode().appendNode("properties").appendNode("hash", hash)
                    }
                }
            }
        }
    }
    repositories {

        if (publishingValid) {
            maven {
                url = uri(mavenUrl!!)

                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        } else {
            mavenLocal()
        }
    }
}

if (file("private.gradle").exists()) {
    apply(from = "private.gradle")
}

//defaultTasks = mutableListOf("clean", "build", "sourcesJar");