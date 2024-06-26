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

    implementation("blue.language:blue-language-java:1.0-SNAPSHOT")

}

tasks.test {
    useJUnitPlatform()
}