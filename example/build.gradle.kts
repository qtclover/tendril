plugins {
    kotlin("jvm")
    application
}

description = "Runnable Tendril demos."

val agentJar = configurations.create("agentJar") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(project(":tendril-kotlin"))
    agentJar(project(":tendril-agent"))
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// ./gradlew :example:run  -> the Kotlin DSL, attaching at runtime
application {
    mainClass = "example.KotlinDemoKt"
    // Opt in to runtime attach, so JDK 21+ does not print its warning.
    applicationDefaultJvmArgs = listOf("-XX:+EnableDynamicAgentLoading")
}

// ./gradlew :example:runJava  -> the Java annotation API, attaching at runtime
tasks.register<JavaExec>("runJava") {
    description = "Runs the Java annotation demo."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "example.JavaDemo"
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// ./gradlew :example:runWithAgent  -> an application that never mentions Tendril,
// hooked from the command line with -javaagent
tasks.register<JavaExec>("runWithAgent") {
    description = "Runs PlainApp, hooked with -javaagent and no code changes."
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "example.PlainApp"
    jvmArgumentProviders.add(AgentArgument(agentJar, "example.AgentHooks"))
}

class AgentArgument(
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val jar: FileCollection,
    @get:Input val hookClasses: String,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-javaagent:${jar.singleFile.absolutePath}=$hookClasses")
}
