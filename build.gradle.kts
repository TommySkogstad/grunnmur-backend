plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    `maven-publish`
}

group = "no.grunnmur"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.3"
val exposedVersion = "1.2.0"

dependencies {
    // Ktor server (compileOnly — apper har sin egen versjon)
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-status-pages:$ktorVersion")
    compileOnly("io.ktor:ktor-server-auth:$ktorVersion")
    compileOnly("io.ktor:ktor-server-auth-jwt:$ktorVersion")

    // Ktor client (compileOnly — for GitHubIssueService)
    compileOnly("io.ktor:ktor-client-core:$ktorVersion")
    compileOnly("io.ktor:ktor-client-cio:$ktorVersion")
    compileOnly("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    compileOnly("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Exposed (compileOnly — apper har sin egen versjon)
    compileOnly("org.jetbrains.exposed:exposed-core:$exposedVersion")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    compileOnly("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // Serialization
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Jakarta Mail (compileOnly — apper har sin egen versjon)
    compileOnly("jakarta.mail:jakarta.mail-api:2.1.5")

    // Flyway (compileOnly — apper har sin egen versjon)
    // Pinnet 11.8.2: 11.20.3 og 12.x er buggy, se MEMORY.md
    compileOnly("org.flywaydb:flyway-core:11.8.2")
    compileOnly("org.flywaydb:flyway-database-postgresql:11.8.2")

    // TOTP (compileOnly — apper har sin egen versjon)
    compileOnly("dev.turingcomplete:kotlin-onetimepassword:2.4.1")

    // Logging
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    testImplementation("io.ktor:ktor-server-auth:$ktorVersion")
    testImplementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // Jakarta Mail (test — trenger implementasjon for å kjøre tester)
    testImplementation("jakarta.mail:jakarta.mail-api:2.1.5")
    testImplementation("org.eclipse.angus:angus-mail:2.0.5")
    testImplementation("org.slf4j:slf4j-simple:2.0.17")

    // TOTP (test — trenger implementasjon for å kjøre tester)
    testImplementation("dev.turingcomplete:kotlin-onetimepassword:2.4.1")

    // Flyway (test) — matcher compileOnly
    testImplementation("org.flywaydb:flyway-core:11.8.2")
    testImplementation("org.flywaydb:flyway-database-postgresql:11.8.2")

    // Exposed (test — trenger implementasjon for å kjøre Exposed-baserte tester)
    testImplementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // H2 (test — in-memory database for Flyway- og Exposed-tester)
    testImplementation("com.h2database:h2:2.4.240")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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
