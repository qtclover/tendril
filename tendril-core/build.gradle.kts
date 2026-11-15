plugins {
    `java-library`
}

description = "Tendril core: runtime bytecode injection on ASM, with a Java fluent and annotation API."

val asmVersion = property("asmVersion") as String

// The agent jar is shipped *inside* this jar as a resource rather than as a normal
// dependency: its classes must only ever be loaded by the bootstrap class loader.
val agentJar = configurations.create("agentJar") {
    isCanBeConsumed = false
    isTransitive = false
}

// Classes for the "hook it before it is loaded" tests. They live outside the test
// source set because JUnit loads every class it scans while discovering tests.
val lateFixtures = sourceSets.create("lateFixtures")

dependencies {
    compileOnly(project(":tendril-agent"))
    agentJar(project(":tendril-agent"))

    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")

    testRuntimeOnly(lateFixtures.output)
    // One test generates an old-format class file by hand.
    testImplementation("org.ow2.asm:asm:$asmVersion")
}

tasks.processResources {
    from(agentJar) {
        into("io/github/qtclover/tendril/internal")
        rename { "tendril-agent.jar" }
    }
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.github.qtclover.tendril",
            "Implementation-Title" to "tendril-core",
            "Implementation-Version" to project.version,
        )
    }
}

// The same tests again, but with the agent given on the command line instead of
// attached at runtime.
val agentTest = tasks.register<Test>("agentTest") {
    description = "Runs the tests with -javaagent instead of self-attach."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgumentProviders.add(AgentArgument(agentJar))
    systemProperty("tendril.test.expectPremain", "true")
}

// And once more with a hook class named in the agent arguments, so that it is
// registered before main() without the application doing anything.
val agentArgsTest = tasks.register<Test>("agentArgsTest") {
    description = "Runs AgentArgumentsTest with -javaagent=<hook class>."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgumentProviders.add(AgentArgument(agentJar, "io.github.qtclover.tendril.fixtures.PremainHooks"))
    systemProperty("tendril.test.agentArgs", "true")
    filter {
        includeTestsMatching("*.AgentArgumentsTest")
    }
}

tasks.check {
    dependsOn(agentTest, agentArgsTest)
}

class AgentArgument(
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val jar: FileCollection,
    @get:Input val hookClasses: String = "",
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf(
        "-javaagent:${jar.singleFile.absolutePath}" + if (hookClasses.isEmpty()) "" else "=$hookClasses"
    )
}
