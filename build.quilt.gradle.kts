import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// The Quilt targets' build script: the same sources as build.gradle.kts, built by
// Quilt Loom instead of Fabric Loom, against Quilt Loader and the Fabric API: Quilt runs
// Fabric mods, and QSL stopped following game versions at 1.21.1. What does not depend
// on the loader - the Java level, the tests, the jar name - is the same as in the
// other scripts.
//
// Quilt Loom is already on this script's classpath: loom-back-compat puts it there,
// told to by the loomx.* lines of the Quilt tables in stonecutter.properties.toml.
// It is applied by hand rather than through loom-back-compat's project plugin, which
// only knows Fabric Loom's plugin ids, so this file has no generated accessors: the
// configurations are named as strings and the extension is asked for by type.
//
// Without a plugins block, nothing points this script's classpath at
// pluginManagement's repositories, so it names its own: Quilt's for Quilt Loom, and
// the ones Quilt Loom's own dependencies come from.
buildscript {
    repositories {
        maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt" }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Like Fabric Loom, Quilt Loom comes in two variants in one jar: one remaps the
// obfuscated game (below 26), the other builds against the unobfuscated one (26+).
val unobfuscated = sc.current.parsed >= "26.1"
apply(plugin = if (unobfuscated) "org.quiltmc.loom.no_remap" else "org.quiltmc.loom.remap")

val loom = the<LoomGradleExtensionAPI>()

// The loader is in the file name and not in the version, as on NeoForge:
// livinghorizon-0.1.0+mc1.21.11-quilt.jar
version = "${property("mod.version")}+mc${sc.current.version}-quilt"
the<BasePluginExtension>().archivesName = property("mod.id") as String

// What the targets share, worked out once: the Java level, the access widener or
// transformer, and the list of mixins. See gradle/target.gradle.kts.
extra["lhGame"] = sc.current.version
extra["lhUnobfuscated"] = unobfuscated
apply(from = rootProject.file("gradle/target.gradle.kts"))
val requiredJava: JavaVersion = project.extra["lhJava"] as JavaVersion
loom.accessWidenerPath = project.extra["lhAccessWidener"] as File

repositories {
    maven("https://maven.quiltmc.org/repository/release/") { name = "Quilt" }
    maven("https://maven.terraformersmc.com/releases") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://maven.caffeinemc.net/releases") {
        name = "CaffeineMC"
        content { includeGroup("net.caffeinemc") }
    }
}

dependencies {
    // The code is annotated with jspecify's @Nullable, which the game ships from 1.21.11 on.
    // Before that it is only needed to compile: an annotation nobody reads at run time.
    if (sc.current.parsed < "1.21.11") "compileOnly"("org.jspecify:jspecify:1.0.0")
    "minecraft"("com.mojang:minecraft:${sc.current.version}")
    // The unobfuscated game carries Mojang's names already; the older ones are
    // remapped to them, as on Fabric.
    if (!unobfuscated) {
        "mappings"(loom.officialMojangMappings())
    }

    // The unobfuscated variant has no mod* configurations: nothing is remapped there.
    "${if (unobfuscated) "implementation" else "modImplementation"}"(
        "org.quiltmc:quilt-loader:${property("deps.quilt_loader")}")

    // The Fabric API, which Quilt Loader runs as it runs any Fabric mod: the events, the
    // key bindings, the commands and the screens are the ones of the Fabric build.
    "${if (unobfuscated) "implementation" else "modImplementation"}"(
        "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    // Only its API, for the settings button; nothing of it ships, nothing requires it.
    "${if (unobfuscated) "compileOnly" else "modCompileOnly"}"(
        "com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }
    // Only Sodium's API, for the mod's pages in its video settings; nothing of it ships. Quilt
    // Loom 1.15 cannot remap the Fabric jar (built by Loom 1.16): the NeoForge one holds the same
    // classes under Mojang's names, which the mod is compiled against anyway.
    findProperty("deps.sodium")?.let { "compileOnly"("net.caffeinemc:sodium-neoforge-api:$it") { isTransitive = false } }

    "testImplementation"(platform("org.junit:junit-bom:${property("deps.junit")}"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

configure<JavaPluginExtension> {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()

        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    named<ProcessResources>("processResources") {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("group", "mod.group")
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
            register("loader", "deps.quilt_loader")
            put("java", requiredJava.majorVersion)
            put("mixins", project.extra["lhMixins"] as String)
            // Only the Forge targets that run under obfuscated names need a refmap.
            put("refmap", "")
            // The names the jar was remapped to. An unobfuscated jar has none to name, but
            // Quilt Loader reads a missing field as its own hashed names and refuses the
            // mod, so it says intermediary as well: Quilt runs every game after 25 under
            // the names it ships with, whatever the field says.
            put("intermediate_mappings", "\"intermediate_mappings\": \"net.fabricmc:intermediary\",")
            // Sodium finds the mod's pages through this entry point, on the targets that have them.
            put("sodium", if (project.findProperty("deps.sodium") != null) "\"sodium:config_api_user\": \"fr.clixmods.livinghorizon.ui.SodiumOptions\"," else "")
        }
        inputs.property("sodium", props.getValue("sodium"))

        inputs.property("java", requiredJava.majorVersion)
        inputs.property("mixins", project.extra["lhMixins"] as String)
        inputs.property("intermediate_mappings", props.getValue("intermediate_mappings"))
        filesMatching(listOf("quilt.mod.json", "livinghorizon.mixins.json", "assets/livinghorizon/version.txt")) { expand(props) }
        // The other loaders' metadata has nothing to say to Quilt.
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml")
        // The access widener is the generated copy, not the source file.
        eachFile { if (name == "livinghorizon.accesswidener" && file.parentFile != (project.extra["lhAccessWidener"] as File).parentFile) exclude() }
        from((project.extra["lhAccessWidener"] as File).parentFile) { include("livinghorizon.accesswidener") }
    }

    withType<Jar> {
        val id = project.property("mod.id")
        inputs.property("mod_id", id)
        from(rootProject.file("LICENSE")) { rename { "${it}_$id" } }
        // The changelog the game shows in its own screen.
        from(rootProject.file("changelog")) { include("en.md", "fr.md"); into("assets/livinghorizon/changelog") }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"

        inputs.property("version", project.property("mod.version"))
        // The jar that ships: remapped below 26, the plain one above.
        from(named<AbstractArchiveTask>(if (unobfuscated) "jar" else "remapJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
