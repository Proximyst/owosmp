import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.charset.StandardCharsets
import java.util.UUID

plugins {
  kotlin("jvm") version "2.4.20"
  id("com.gradleup.shadow") version "9.6.1"
  id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
  id("com.diffplug.spotless") version "8.10.3"
}

repositories {
  mavenCentral()
}

val shadow = configurations.getByName("shadow")
configurations.implementation.get().extendsFrom(shadow)

val devPlayerName = "Player"
val devPlayerUuid =
  UUID.nameUUIDFromBytes("OfflinePlayer:$devPlayerName".toByteArray(StandardCharsets.UTF_8))
    .toString()

loom {
  splitEnvironmentSourceSets()

  mods {
    create("owosmp") {
      sourceSet(sourceSets.main.get())
    }
  }

  runs {
    named("client") {
      programArguments.addAll("--username", devPlayerName)
    }
  }
}

dependencies {
  minecraft("com.mojang:minecraft:26.3")
  implementation("net.fabricmc:fabric-loader:0.19.5")
  implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.3")

  add(shadow.name, kotlin("stdlib"))
  add(shadow.name, "com.openai:openai-java:4.78.0")
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
  // Thin jar must not collide with the shadowed jar
  archiveClassifier.set("thin")
}

tasks.build { dependsOn(tasks.named("shadowJar")) }

val shadowJar =
  tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadow)
    // Make the shadowed jar the deployable artifact (owosmp.jar)
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    // Relocate bundled third-party packages, except Kotlin (kotlin-reflect breaks when relocated)
    val prefix = "dev.mardroemmar.owosmp.shaded"
    listOf(
      "com.openai",
      "okhttp3",
      "okio",
      "com.fasterxml"
    )
      .forEach { relocate(it, "$prefix.$it") }
    // Minecraft already ships SLF4J; use its copy
    exclude("org/slf4j/**")
  }

abstract class PrepareServerRunTask : DefaultTask() {
  @get:Input
  abstract val playerName: Property<String>

  @get:Input
  abstract val playerUuid: Property<String>

  @get:Internal
  abstract val runDir: DirectoryProperty

  @TaskAction
  fun prepare() {
    val dir = runDir.get().asFile
    dir.mkdirs()

    val name = playerName.get()
    val uuid = playerUuid.get()

    // 1. Agree to EULA
    dir.resolve("eula.txt").writeText("eula=true\n")

    // 2. Disable online-mode and ensure whitelist in server.properties
    val propsFile = dir.resolve("server.properties")
    if (propsFile.exists()) {
      var content = propsFile.readText()
      content =
        if (content.contains("online-mode=")) {
          content.replace(Regex("""online-mode=.*"""), "online-mode=false")
        } else {
          "$content\nonline-mode=false\n"
        }
      propsFile.writeText(content)
    } else {
      propsFile.writeText("online-mode=false\nwhite-list=true\n")
    }

    // 3. Auto-op dev player
    val opsFile = dir.resolve("ops.json")
    val opsJson =
      if (opsFile.exists() && opsFile.readText().isNotBlank()) {
        com.google.gson.JsonParser.parseString(opsFile.readText()).asJsonArray
      } else {
        com.google.gson.JsonArray()
      }
    val opEntry =
      opsJson
        .mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
        .firstOrNull { it.get("name")?.asString.equals(name, ignoreCase = true) }
    if (opEntry != null) {
      opEntry.addProperty("uuid", uuid)
      opEntry.addProperty("level", 4)
    } else {
      val entry =
        com.google.gson.JsonObject().apply {
          addProperty("uuid", uuid)
          addProperty("name", name)
          addProperty("level", 4)
          addProperty("bypassesPlayerLimit", false)
        }
      opsJson.add(entry)
    }
    opsFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(opsJson))

    // 4. Auto-whitelist dev player
    val whitelistFile = dir.resolve("whitelist.json")
    val whitelistJson =
      if (whitelistFile.exists() && whitelistFile.readText().isNotBlank()) {
        com.google.gson.JsonParser.parseString(whitelistFile.readText()).asJsonArray
      } else {
        com.google.gson.JsonArray()
      }
    val wlEntry =
      whitelistJson
        .mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
        .firstOrNull { it.get("name")?.asString.equals(name, ignoreCase = true) }
    if (wlEntry != null) {
      wlEntry.addProperty("uuid", uuid)
    } else {
      val entry =
        com.google.gson.JsonObject().apply {
          addProperty("uuid", uuid)
          addProperty("name", name)
        }
      whitelistJson.add(entry)
    }
    whitelistFile.writeText(
      com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(whitelistJson)
    )
  }
}

val prepareServerRun =
  tasks.register<PrepareServerRunTask>("prepareServerRun") {
    playerName.set(devPlayerName)
    playerUuid.set(devPlayerUuid)
    runDir.set(layout.projectDirectory.dir("run"))
    outputs.upToDateWhen { false }
  }

tasks.named("runServer") { dependsOn(prepareServerRun) }

spotless {
  java {
    googleJavaFormat()
  }
  kotlin {
    ktfmt()
  }
}
