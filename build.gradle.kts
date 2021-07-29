import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
	kotlin("jvm") version "1.5.21"
    `java-library`
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "1.5.0"
    id("org.javamodularity.moduleplugin") version "1.8.7"
}

group = "org.rationalityfrontline"
version = "2.0.1"
val NAME = "kevent"
val DESC = "A powerful in-process event dispatcher based on Kotlin and Coroutines"
val GITHUB_REPO = "RationalityFrontline/kevent"

repositories {
    mavenCentral()
}

dependencies {
    val coroutinesVersion = "1.5.1"
    /** Kotlin --------------------------------------------------------- */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
    /** Logging -------------------------------------------------------- */
    implementation("io.github.microutils:kotlin-logging:2.0.10")
    val spekVersion = "2.0.15"
    /** Logging -------------------------------------------------------- */
    testImplementation("org.slf4j:slf4j-simple:1.7.30")
    testImplementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.13.0")
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
        testLogging.showStandardStreams = true
        useJUnitPlatform {
            doFirst {
                classpath.forEach { it.mkdirs() }
            }
            jvmArgs = listOf(
                "--add-exports", "org.junit.platform.commons/org.junit.platform.commons.util=ALL-UNNAMED",
                "--add-exports", "org.junit.platform.commons/org.junit.platform.commons.logging=ALL-UNNAMED",
                "--add-reads", "kevent=spek.dsl.jvm",
                "--add-reads", "kevent=kotlin.test",
                "--add-reads", "kevent=java.desktop"
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            pom {
                name.set(NAME)
                description.set(DESC)
                packaging = "jar"
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
                        organization.set("RationalityFrontline")
                        organizationUrl.set("https://github.com/RationalityFrontline")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/$GITHUB_REPO.git")
                    developerConnection.set("scm:git:ssh://github.com:$GITHUB_REPO.git")
                    url.set("https://github.com/$GITHUB_REPO/tree/master")
                }
            }
        }
    }
    repositories {
        fun env(propertyName: String): String {
            return if (project.hasProperty(propertyName)) {
                project.property(propertyName) as String
            } else "Unknown"
        }
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = env("ossrhUsername")
                password = env("ossrhPassword")
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}