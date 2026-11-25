plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "cat.inslaferreria"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("cat.inslaferreria.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
