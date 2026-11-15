plugins {
    `java-library`
}

description = "Tendril agent jar: Java agent entry points and the bootstrap bridge. No dependencies."

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "io.github.qtclover.tendril.agent.Agent",
            "Agent-Class" to "io.github.qtclover.tendril.agent.Agent",
            "Can-Retransform-Classes" to "true",
            "Can-Redefine-Classes" to "true",
            "Implementation-Title" to "tendril-agent",
            "Implementation-Version" to project.version,
        )
    }
}
