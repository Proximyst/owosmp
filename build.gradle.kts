plugins {
  kotlin("jvm") version "2.4.20"
  // id("com.gradleup.shadow") version "9.6.1"
  id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
  id("com.diffplug.spotless") version "8.10.3"
}

repositories {
  mavenCentral()
}

loom {
  splitEnvironmentSourceSets()

  mods {
    create("owosmp") {
      sourceSet(sourceSets.main.get())
    }
  }
}

dependencies {
  minecraft("com.mojang:minecraft:26.3")
  implementation("net.fabricmc:fabric-loader:0.19.5")
  implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.3")

  implementation(kotlin("stdlib"))
  implementation("com.openai:openai-java:4.78.0")
}

tasks.processResources {
  val ver = rootProject.version.toString()
  inputs.property("version", ver)
  filesMatching("fabric.mod.json") {
    expand("version" to ver)
  }
}

java {
  withSourcesJar()
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = sourceCompatibility
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.release.set(java.sourceCompatibility.majorVersion.toInt())
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
  }
}

tasks.jar {
  inputs.property("projectName", rootProject.name)
}

spotless {
  java {
    googleJavaFormat()
  }
  kotlin {
    ktfmt()
  }
}
