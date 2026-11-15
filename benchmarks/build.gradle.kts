plugins {
    java
}

description = "JMH benchmarks for the cost of a hooked call. Not part of the build; run with :benchmarks:jmh."

val jmhVersion = "1.37"

dependencies {
    implementation(project(":tendril-core"))
    implementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}

// ./gradlew :benchmarks:jmh                      all benchmarks, results in build/jmh.json
// ./gradlew :benchmarks:jmh -PjmhArgs="-f 3 direct"  any JMH options
tasks.register<JavaExec>("jmh") {
    description = "Runs the benchmarks."
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    val results = layout.buildDirectory.file("jmh.json")
    outputs.file(results)
    outputs.upToDateWhen { false }
    args("-rf", "json", "-rff", results.get().asFile.absolutePath,
        // The forked benchmark JVM attaches the agent at runtime.
        "-jvmArgsAppend", "-XX:+EnableDynamicAgentLoading -Xshare:off")
    args((project.findProperty("jmhArgs") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList<String>())
}
