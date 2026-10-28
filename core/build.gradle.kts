plugins {
    alias(libs.plugins.lombok)
}

description = "Platform-agnostic core of Catalog: Modrinth client, jar index, tracking and update logic."

dependencies {
    compileOnly(libs.gson)
    compileOnly(libs.commons)
    compileOnly(libs.adventure.api)
    compileOnly(libs.lamp.common)
    compileOnly(libs.okaeri.core)

    testImplementation(libs.junit)
    testImplementation(libs.gson)
    testImplementation(libs.commons)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.text.serializer.plain)
    testImplementation(libs.lamp.common)
    testRuntimeOnly(libs.paper)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs the tests that call the live Modrinth API."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("integration")
    }

    outputs.upToDateWhen { false }
}
