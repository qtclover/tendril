// A separate build, so that the library's own build does not download Spring Boot.
// Run it with the wrapper of the main build:  ../../gradlew -p demo/spring-jdbc <task>
rootProject.name = "tendril-spring-demo"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Use Tendril from this checkout rather than from a repository.
includeBuild("../..")

include("app", "hooks")
