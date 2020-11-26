import com.jfrog.bintray.gradle.BintrayExtension.PackageConfig
import com.jfrog.bintray.gradle.BintrayExtension.VersionConfig
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import java.util.*

plugins {
	kotlin("jvm") version "1.4.10"
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka") version "1.4.10"
    id("org.javamodularity.moduleplugin") version "1.7.0"
    id("com.jfrog.bintray") version "1.8.5"
}

group = "org.rationalityfrontline"
version = "1.0.0-dev.001"
val SDK_NAME = "KEvent"
val SDK_VERSION = version.toString()

repositories {
    jcenter()
}


dependencies {
    val coroutinesVersion = "1.3.9"
    /** Kotlin --------------------------------------------------------- */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
    /** Logging -------------------------------------------------------- */
    implementation("io.github.microutils:kotlin-logging:2.0.3")
    val spekVersion = "2.0.14"
    /** Logging -------------------------------------------------------- */
    testImplementation("org.slf4j:slf4j-simple:1.7.30")
    testImplementation("org.jetbrains.kotlin:kotlin-test:${getKotlinPluginVersion()}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
    testRuntimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

tasks {
    dokkaHtml {
        outputDirectory.set(buildDir.resolve("javadoc"))
        moduleName.set("KEvent")
        dokkaSourceSets {
            named("main") {
                includes.from("module.md")
            }
        }
    }
    test {
        useJUnitPlatform {
            doFirst {
                classpath.forEach { it.mkdirs() }
            }
            jvmArgs = listOf(
                "--add-exports", "org.junit.platform.commons/org.junit.platform.commons.util=ALL-UNNAMED",
                "--add-exports", "org.junit.platform.commons/org.junit.platform.commons.logging=ALL-UNNAMED",
                "--add-reads", "kevent=spek.dsl.jvm",
                "--add-reads", "kevent=kotlin.test"
            )
            includeEngines("spek2")
        }
    }
    register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        from(dokkaHtml)
    }
    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
}

val NAME = "kevent"
val DESC = "A powerful event dispatcher"
val GITHUB_REPO = "RationalityFrontline/kevent"

publishing {
    publications {
        create<MavenPublication>("mavenPublish") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            pom {
                name.set(NAME)
                description.set("$NAME ${project.version} - $DESC")
                url.set("https://github.com/$GITHUB_REPO")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("RationalityFrontline")
                        email.set("rationalityfrontline@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/$GITHUB_REPO")
                }
            }
        }
    }
}

bintray {
    fun env(propertyName: String): String {
        return if (project.hasProperty(propertyName)) {
            project.property(propertyName) as String
        } else "Unknown"
    }

    user = env("BINTRAY_USER")
    key = env("BINTRAY_KEY")
    publish = true
    override = true
    setPublications("mavenPublish")
    pkg(closureOf<PackageConfig>{
        repo = "kevent"
        name = if (project.version.toString().contains("dev")) "$NAME-dev" else NAME
        desc = DESC
        setLabels("kotlin", "eventbus")
        setLicenses("Apache-2.0")
        publicDownloadNumbers = true
        githubRepo = GITHUB_REPO
        vcsUrl = "https://github.com/$githubRepo"
        websiteUrl = vcsUrl
        issueTrackerUrl = "$vcsUrl/issues"
        version(closureOf<VersionConfig> {
            name = "${project.version}"
            desc = DESC
            released = "${Date()}"
            vcsTag = name
        })
    })
}