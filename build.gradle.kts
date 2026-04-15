plugins {
    `maven-publish`
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("plugin.serialization") version "2.3.20"
}

repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        mavenContent {
            includeGroup("org.vineflower")
        }
    }
    maven("https://maven.fabricmc.net/") {
        mavenContent {
            includeGroup("net.fabricmc.unpick")
        }
    }
    mavenCentral()
}

dependencies {
    compileOnly("org.vineflower:vineflower:1.12.0-SNAPSHOT") // user can provide their own version
    implementation("net.fabricmc.unpick:unpick:3.0.0-beta.13")
    implementation("net.fabricmc.unpick:unpick-format-utils:3.0.0-beta.13")
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.ow2.asm:asm-tree:9.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

kotlin {
    jvmToolchain(25)
}

gradlePlugin {
    vcsUrl = "https://github.com/Lulu13022002/mc-source"

    plugins {
        create("mc-source") {
            id = project.group.toString()
            implementationClass = "dev.lulu.plugin.McSource"
            tags = setOf("minecraft", "source", "diff")
        }
    }
}
