plugins {
    application
    java
}

group = "spbpu.md"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("spbpu.md.converter.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.commons:commons-collections4:4.4")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "spbpu.md.converter.Main"
    }
}
