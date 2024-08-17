plugins {
    id("java")
}

group = "nub.wi1helm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("net.minestom:minestom-snapshots:19c4b5d598")
}

tasks.test {
    useJUnitPlatform()
}