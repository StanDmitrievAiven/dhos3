plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val opensearchJavaVersion = "3.7.0"

dependencies {
    implementation("org.opensearch.client:opensearch-java:$opensearchJavaVersion")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.4.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("io.aiven.dhos3.spike.SpikeMain")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
