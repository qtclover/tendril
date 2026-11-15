plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

description = "Tendril standalone agent: agent, core and a private copy of ASM in one jar, " +
    "for -javaagent without other dependencies and for attaching to a running JVM."

dependencies {
    implementation(project(":tendril-core"))
    implementation(project(":tendril-agent"))
}

tasks.shadowJar {
    archiveClassifier = ""
    // A private copy, so the ASM version of the hooked application never matters.
    relocate("org.objectweb.asm", "io.github.qtclover.tendril.shaded.asm")
    // The bridge may only ever be defined by the bootstrap loader. It gets there from
    // the plain agent jar that tendril-core carries inside it, not from this jar.
    exclude("io/github/qtclover/tendril/agent/Bridge*.class")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/**")
    manifest {
        attributes(
            "Premain-Class" to "io.github.qtclover.tendril.agent.Agent",
            "Agent-Class" to "io.github.qtclover.tendril.agent.Agent",
            "Main-Class" to "io.github.qtclover.tendril.agent.Cli",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
            "Implementation-Title" to "tendril-agent-all",
            "Implementation-Version" to project.version,
        )
    }
}

// The shaded jar is this module's one artifact.
tasks.jar {
    enabled = false
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Published as a plain jar without dependencies: everything is inside. Maven Central
// insists on a sources and a javadoc jar; the sources are those of the other modules.
val pointer = tasks.register<WriteProperties>("pointer") {
    destinationFile = layout.buildDirectory.file("pointer/README.properties")
    property("contents", "Shaded bundle of tendril-agent, tendril-core and ASM. See those artifacts for sources and javadoc.")
}
val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier = "sources"
    from(pointer)
}
val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
    from(pointer)
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifact(tasks.shadowJar)
        artifact(sourcesJar)
        artifact(javadocJar)
    }
}

// Two tiny programs for the tests: an application that knows nothing about Tendril,
// and a jar of hooks for it.
val targetApp = sourceSets.create("targetApp")
val hooks = sourceSets.create("hooks")
dependencies {
    "hooksCompileOnly"(project(":tendril-core"))
}
val hooksJar = tasks.register<Jar>("hooksJar") {
    archiveBaseName = "demo-hooks"
    from(hooks.output)
}

tasks.test {
    dependsOn(tasks.shadowJar, hooksJar, targetApp.classesTaskName)
    val agent = tasks.shadowJar.flatMap { it.archiveFile }
    val hooksFile = hooksJar.flatMap { it.archiveFile }
    val appClasses = targetApp.output.classesDirs
    inputs.file(agent)
    inputs.file(hooksFile)
    inputs.files(appClasses)
    doFirst {
        systemProperty("tendril.test.agentJar", agent.get().asFile.absolutePath)
        systemProperty("tendril.test.hooksJar", hooksFile.get().asFile.absolutePath)
        systemProperty("tendril.test.appClasses", appClasses.asPath)
    }
}
