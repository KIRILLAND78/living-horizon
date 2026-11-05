plugins {
    id("dev.kikugie.loom-back-compat")
}

// mod version and Minecraft version stay separate: livinghorizon-0.1.0+mc1.21.11.jar
version = "${property("mod.version")}+mc${sc.current.version}"
base.archivesName = property("mod.id") as String

repositories {
    maven("https://maven.terraformersmc.com/releases") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://maven.caffeinemc.net/releases") {
        name = "CaffeineMC"
        content { includeGroup("net.caffeinemc") }
    }
}

// What the targets share, worked out once: the Java level, the access widener or
// transformer, and the list of mixins. See gradle/target.gradle.kts.
extra["lhGame"] = sc.current.version
extra["lhUnobfuscated"] = sc.current.parsed >= "26.1"
apply(from = rootProject.file("gradle/target.gradle.kts"))
val requiredJava: JavaVersion = project.extra["lhJava"] as JavaVersion

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // The code is annotated with jspecify's @Nullable, which the game ships from 1.21.11 on.
    // Before that it is only needed to compile: an annotation nobody reads at run time.
    if (sc.current.parsed < "1.21.11") compileOnly("org.jspecify:jspecify:1.0.0")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    // The whole Fabric API: module names change between game versions (the key binding
    // module became a key mapping one in 26.1), and the full jar is one coordinate on all
    // of them. The mod only needs the lifecycle, key binding, command, screen and
    // networking modules at run time.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"] as String}")
    // Only its API, for the settings button; nothing of it ships, nothing requires it.
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }
    // Only Sodium's API, for the mod's pages in its video settings; nothing of it ships.
    findProperty("deps.sodium")?.let { modCompileOnly("net.caffeinemc:sodium-fabric-api:$it") { isTransitive = false } }

    testImplementation(platform("org.junit:junit-bom:${property("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Client game tests: the real game, driven by a script, taking screenshots. See
// .claude/skills/game-test/SKILL.md. Not part of `build`; run with `runClientGameTest`.
// They run on every target that has Fabric's client game test API with the methods the
// scenarios use, 1.21.4 and later; before 1.21.9 they check the models alone, impostors being
// off there.
val gametests = sc.current.parsed >= "1.21.4"

if (gametests) {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "livinghorizon-gametest"
            enableGameTests = false
            enableClientGameTests = true
            eula = true
        }
    }


    dependencies {
        "modGametestImplementation"(fabricApi.module("fabric-client-gametest-api-v1", sc.properties["deps.fabric_api"]))
    }

    // Not run by `build` (it needs a display), but compiled, so that the scenarios never rot.
    tasks.named("check") { dependsOn("compileGametestJava") }
}

loom {
    // Opens the classes and fields the client never exposes by itself.
    accessWidenerPath = project.extra["lhAccessWidener"] as File

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
    // Its own folder, emptied before every run: never the one runClient plays in.
    if (gametests) runConfigs.named("clientGameTest") {
        runDirectory = rootProject.file("build/run/clientGameTest")
        property("fabric.client.gametest.testModResourcesPath", rootProject.file("src/gametest/resources").absolutePath)
        // Which scenarios to run: -Plh.scenario=impostors,... (all of them by default).
        property("livinghorizon.scenario", (findProperty("lh.scenario") ?: "all").toString())
        // Which easter eggs the eggs2 scenario runs: -Plh.eggs=9,12 or LH_EGGS=9,12 (all by default).
        (findProperty("lh.eggs") ?: System.getenv("LH_EGGS"))?.let { property("livinghorizon.eggs", it.toString()) }
        // Other mods to load with it (Sodium...): -Plh.mods=<folder of jars>.
        findProperty("lh.mods")?.let { property("fabric.addMods", it.toString()) }
        // A shader pack folder to turn on through Iris, which -Plh.mods must hold: -Plh.shaderpack=<folder>.
        findProperty("lh.shaderpack")?.let { property("livinghorizon.shaderpack", it.toString()) }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
}

tasks {
    test {
        useJUnitPlatform()
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }

    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
            register("loader", "mod.loader_min")
            put("java", requiredJava.majorVersion)
            put("mixins", project.extra["lhMixins"] as String)
            // Loom names its own refmap in the config: the placeholder is for the older Forge.
            put("refmap", "")
            // Sodium finds the mod's pages through this entry point, on the targets that have them.
            put("sodium", if (project.findProperty("deps.sodium") != null) "\"sodium:config_api_user\": [\"fr.clixmods.livinghorizon.ui.SodiumOptions\"]," else "")
        }
        inputs.property("sodium", props.getValue("sodium"))
        inputs.property("java", requiredJava.majorVersion)
        inputs.property("mixins", project.extra["lhMixins"] as String)
        filesMatching(listOf("fabric.mod.json", "livinghorizon.mixins.json", "assets/livinghorizon/version.txt")) { expand(props) }
        // The other loaders' metadata has nothing to say to Fabric.
        exclude("quilt.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml")
        // The access widener is the generated copy, not the source file.
        eachFile { if (name == "livinghorizon.accesswidener" && file.parentFile != (project.extra["lhAccessWidener"] as File).parentFile) exclude() }
        from((project.extra["lhAccessWidener"] as File).parentFile) { include("livinghorizon.accesswidener") }

        // The license and the credits travel with the jar.
        from(rootProject.file("LICENSE"))
        // The changelog the game shows in its own screen.
        from(rootProject.file("changelog")) { include("en.md", "fr.md"); into("assets/livinghorizon/changelog") }
        from(rootProject.file("CREDITS.md"))
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"
        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
