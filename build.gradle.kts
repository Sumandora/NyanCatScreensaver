import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.8.21"
  id("org.jetbrains.intellij") version "1.13.3"
}

group = "su.mandora"
version = "1.0"

repositories {
  mavenCentral()
}

intellij {
  version.set("2023.2")
  updateSinceUntilBuild.set(false)
}

tasks {
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
  }
}
