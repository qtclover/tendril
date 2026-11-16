plugins {
    java
}

// The hooks only need Tendril to compile. At run time it comes from the agent jar.
dependencies {
    compileOnly("io.github.qtclover:tendril-core")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}
