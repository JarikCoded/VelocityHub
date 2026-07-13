plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "de.velocityhub"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Velocity API
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    // Redis client
    implementation("redis.clients:jedis:5.1.5")

    // YAML configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.11.0")

    // HTTP client for Discord webhooks
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    javadoc {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("redis.clients.jedis", "de.velocityhub.libs.jedis")
        relocate("org.apache.commons.pool2", "de.velocityhub.libs.pool2")
        relocate("org.spongepowered.configurate", "de.velocityhub.libs.configurate")
        relocate("com.google.gson", "de.velocityhub.libs.gson")
        relocate("okhttp3", "de.velocityhub.libs.okhttp3")
        relocate("okio", "de.velocityhub.libs.okio")
        minimize {
            exclude(dependency("redis.clients:jedis:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
