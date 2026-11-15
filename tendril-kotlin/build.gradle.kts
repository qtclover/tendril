plugins {
    kotlin("jvm")
}

description = "Tendril Kotlin DSL."

// See tendril-core: classes that must not be loaded while JUnit discovers tests.
val lateFixtures = sourceSets.create("lateFixtures")

dependencies {
    api(project(":tendril-core"))

    testImplementation(kotlin("test"))
    testRuntimeOnly(lateFixtures.output)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
    explicitApi()
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.github.qtclover.tendril.dsl",
            "Implementation-Title" to "tendril-kotlin",
            "Implementation-Version" to project.version,
        )
    }
}
