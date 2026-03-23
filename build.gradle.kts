plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.20"
    `maven-publish`
}

group = "no.grunnmur"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.1"
val exposedVersion = "0.61.0"

dependencies {
    // Ktor (compileOnly — apper har sin egen versjon)
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Exposed (compileOnly — apper har sin egen versjon)
    compileOnly("org.jetbrains.exposed:exposed-core:$exposedVersion")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    compileOnly("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // Serialization
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Logging
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "no.grunnmur"
            artifactId = "grunnmur"
            version = project.version.toString()
        }
    }
}
