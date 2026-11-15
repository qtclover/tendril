rootProject.name = "tendril"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("tendril-agent", "tendril-core", "tendril-kotlin", "example")
