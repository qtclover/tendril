plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

// An ordinary Spring Boot application. Note what is missing: any mention of Tendril.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("com.h2database:h2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
    }
}
