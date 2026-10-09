import java.nio.charset.StandardCharsets
import java.util.UUID
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
    id("com.diffplug.spotless") version "8.10.3"
}

version = "0.1.0"

repositories {
    mavenCentral()
}

val devPlayerName = "Player"
val devPlayerUuid =
    UUID.nameUUIDFromBytes("OfflinePlayer:$devPlayerName".toByteArray(StandardCharsets.UTF_8))
        .toString()

loom {
    splitEnvironmentSourceSets()
    log4jConfigs.from(file("log4j2.xml"))

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

val transitiveInclude = configurations.create("transitiveInclude") {
    exclude(group = "com.mojang")
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.3")

    annotationProcessor("org.immutables:value:2.12.2")
    compileOnly("org.immutables:value-annotations:2.12.2")
    compileOnly("org.immutables:gson:2.12.2")

    transitiveInclude(implementation("com.openai:openai-java:4.78.0")!!)

    transitiveInclude.resolvedConfiguration.resolvedArtifacts.forEach {
        include(it.moduleVersion.id.toString())
    }
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

tasks.withType<RemapJarTask> {
    addNestedDependencies.set(true)
}

tasks.jar {
    inputs.property("projectName", rootProject.name)
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
        opsFile.writeText(
            com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(opsJson)
        )

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
