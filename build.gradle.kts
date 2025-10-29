plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application { mainClass.set("Main") }

group = "org.example"
version = "1.0.0"

repositories { mavenCentral() }

dependencies {

    // Kotlin BOM
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    // Kotlinx
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.json)

    // Ktor 3.x (совместим с Koog 0.5)
    implementation(libs.ktor.json)

    // Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content)
    implementation(libs.ktor.client.logging)

    // Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call)

    // Koog
    implementation(libs.koog.agents)

    // Simple Logging Facade for Java
    implementation(libs.slf4j.simple)

}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}