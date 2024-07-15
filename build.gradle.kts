plugins {
    id("java")
}

group = "blue.contract"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // JUnit Jupiter (JUnit 5)
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")

    // GraalVM JS Engine
    implementation("org.graalvm.sdk:graal-sdk:23.0.4")
    implementation("org.graalvm.js:js:23.0.4")
    implementation("org.graalvm.js:js-scriptengine:23.0.4")

    // JSON Patch
    implementation("com.github.java-json-tools:json-patch:1.13")

    // Blue Language
    implementation("blue.language:blue-language-java:1.0-SNAPSHOT")

}

tasks.test {
    useJUnitPlatform()
}