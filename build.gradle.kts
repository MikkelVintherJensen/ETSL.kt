plugins {
    kotlin("jvm") version "2.3.20"
    application
}

group = "dk.mikkel.etsl"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("syntactic_analysis.MainKt")
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")
        }
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}

tasks.register<JavaExec>("generateParser") {
    group = "cocor"
    description = "Generate Scanner.java and Parser.java from CocoR/ETSL.ATG"

    workingDir = file("CocoR")
    classpath = files("CocoR/Coco.jar")

    args(
        "ETSL.ATG",
        "-package", "syntactic_analysis",
        "-o", "../src/main/java/syntactic_analysis"
    )

    inputs.file("CocoR/ETSL.ATG")
    inputs.file("CocoR/Coco.jar")
    inputs.file("CocoR/Parser.frame")
    inputs.file("CocoR/Scanner.frame")
    outputs.dir("src/main/java/syntactic_analysis")
}

tasks.named("compileJava") {
    dependsOn("generateParser")
}

tasks.named("compileKotlin") {
    dependsOn("generateParser")
}

tasks.register<JavaExec>("runExample") {
    group = "application"
    description = "Run interpreter on Examples/hello.etsl"

    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("syntactic_analysis.MainKt")
    args("Examples/hello.etsl")
}