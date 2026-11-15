import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.4.21" apply false
}

subprojects {
    // The three library modules can be installed with ./gradlew publishToMavenLocal.
    if (name.startsWith("tendril-")) {
        apply(plugin = "maven-publish")
        plugins.withType<JavaPlugin> {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
            }
            extensions.configure<PublishingExtension> {
                publications.create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name = project.name
                        description = provider { project.description }
                        url = "https://github.com/qtclover/tendril"
                        licenses {
                            license {
                                name = "MIT License"
                                url = "https://opensource.org/license/mit"
                            }
                        }
                    }
                }
            }
        }
    }

    val junitVersion = property("junitVersion") as String

    plugins.withType<JavaPlugin> {
        // Java 17 bytecode, whatever JDK runs the build.
        tasks.withType<JavaCompile>().configureEach {
            options.release = 17
            options.encoding = "UTF-8"
            options.compilerArgs.add("-Xlint:all,-serial,-processing")
        }

        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:$junitVersion"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            // JDK 21+ warns when an agent is loaded into a running JVM. The tests
            // do exactly that on purpose, so opt in and keep the output clean.
            jvmArgs("-XX:+EnableDynamicAgentLoading")
            testLogging {
                events("passed", "skipped", "failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}
