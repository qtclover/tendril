import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.4.21" apply false
}

subprojects {
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
