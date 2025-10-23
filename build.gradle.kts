plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    application
}

group = "org.example"
version = "1.0.0"

repositories { mavenCentral() }

dependencies {

    // Kotlin BOM
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.2.10"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Ktor 3.x (совместим с Koog 0.5)
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")

    // Client
    implementation("io.ktor:ktor-client-core:3.3.0")
    implementation("io.ktor:ktor-client-cio:3.3.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-client-logging:3.3.0")

    // Server
    implementation("io.ktor:ktor-server-core:3.3.0")
    implementation("io.ktor:ktor-server-netty:3.3.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.3.0")
    implementation("io.ktor:ktor-server-cors:3.3.0")
    implementation("io.ktor:ktor-server-call-logging:3.3.0")

    // Koog
    implementation("ai.koog:koog-agents:0.5.0")

    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
}

application { mainClass.set("MainKt") }

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}