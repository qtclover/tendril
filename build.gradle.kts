import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.4.21" apply false
}

subprojects {
    val junitVersion = property("junitVersion") as String

    // The library modules are published; the example and the benchmarks are not.
    // tendril-agent-all publishes its shaded jar and declares that publication itself.
    if (name.startsWith("tendril-")) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        if (name != "tendril-agent-all") {
            plugins.withType<JavaPlugin> {
                extensions.configure<JavaPluginExtension> {
                    withSourcesJar()
                    withJavadocJar()
                }
                extensions.configure<PublishingExtension> {
                    publications.create<MavenPublication>("maven") {
                        from(components["java"])
                    }
                }
            }
        }

        extensions.configure<PublishingExtension> {
            // Maven Central wants every POM to say what the thing is, whose it is and
            // under which licence.
            publications.withType<MavenPublication>().configureEach {
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
                    developers {
                        developer {
                            id = "qtclover"
                            name = "qtclover"
                            url = "https://github.com/qtclover"
                        }
                    }
                    scm {
                        url = "https://github.com/qtclover/tendril"
                        connection = "scm:git:https://github.com/qtclover/tendril.git"
                        developerConnection = "scm:git:ssh://git@github.com/qtclover/tendril.git"
                    }
                }
            }
            // ./gradlew publishAllPublicationsToStagingRepository lays everything out in
            // build/staging-deploy, which the release workflow uploads to Maven Central.
            repositories {
                maven {
                    name = "staging"
                    url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
                }
            }
        }

        // Signed only when a key is supplied (the release workflow does), so that
        // publishToMavenLocal keeps working for everyone else.
        val signingKey = providers.gradleProperty("signingKey").orNull
        if (signingKey != null) {
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").orNull)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }

        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
        }
    }

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

// ./gradlew apiDocs  ->  build/site: the Javadoc of tendril-core and the KDoc of
// tendril-kotlin behind one index page. The docs workflow publishes it to GitHub Pages.
tasks.register<Sync>("apiDocs") {
    description = "Assembles the API documentation site."
    group = "documentation"
    dependsOn(":tendril-core:javadoc", ":tendril-kotlin:dokkaGeneratePublicationHtml")
    into(layout.buildDirectory.dir("site"))
    from("docs/site")
    from(project(":tendril-core").layout.buildDirectory.dir("docs/javadoc")) { into("java") }
    from(project(":tendril-kotlin").layout.buildDirectory.dir("dokka/html")) { into("kotlin") }
}
