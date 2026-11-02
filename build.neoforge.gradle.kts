import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// The NeoForge targets' build script: the same sources as build.gradle.kts, built by
// ModDevGradle instead of Loom. What does not depend on the loader - the Java level,
// the engine repository, the tests, the jar name - is kept word for word the same as
// there and in build.quilt.gradle.kts, so a change to one of those belongs in all three.
plugins {
    id("net.neoforged.moddev")
}

// The loader is in the file name and not in the version: the Fabric release globs
// match "+mc<version>.jar", and a NeoForge jar must not answer to them.
// livinghorizon-0.1.0+mc1.21.11-neoforge.jar
version = "${property("mod.version")}+mc${sc.current.version}-neoforge"
base.archivesName = property("mod.id") as String

// What the targets share, worked out once: the Java level, the access transformer, and the
// list of mixins. See gradle/target.gradle.kts.
extra["lhGame"] = sc.current.version
extra["lhUnobfuscated"] = sc.current.parsed >= "26.1"
apply(from = rootProject.file("gradle/target.gradle.kts"))
val requiredJava: JavaVersion = project.extra["lhJava"] as JavaVersion



val lhAccessTransformer: File = project.extra["lhAccessTransformer"] as File

neoForge {
    version = sc.properties["deps.neoforge"]
    accessTransformers.from(lhAccessTransformer)

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    // The tests start the game's registries, which on NeoForge needs its loader running:
    // the plugin sets that up for the tests of the mod it is told about.
    unitTest {
        enable()
        testedMod = mods.getByName(property("mod.id") as String)
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run")
        }
    }

    // The tests speak in Component and PlayerModelType, like on Fabric. NeoForge
    // patches those classes, so the tests need NeoForge on their classpath as well
    // as the game.
    addModdingDependenciesTo(sourceSets.test.get())
}

repositories {
    maven("https://maven.caffeinemc.net/releases") {
        name = "CaffeineMC"
        content { includeGroup("net.caffeinemc") }
    }
}

dependencies {
    // The code is annotated with jspecify's @Nullable, which the game ships from 1.21.11 on.
    // Before that it is only needed to compile: an annotation nobody reads at run time.
    if (sc.current.parsed < "1.21.11") compileOnly("org.jspecify:jspecify:1.0.0")
    // Only Sodium's API, for the mod's pages in its video settings; nothing of it ships.
    findProperty("deps.sodium")?.let { compileOnly("net.caffeinemc:sodium-neoforge-api:$it") { isTransitive = false } }
    testImplementation(platform("org.junit:junit-bom:${property("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    test {
        useJUnitPlatform()
        // The tests read the sources by a path relative to the target's
        // folder, which is where Loom's test task runs; ModDevGradle's runs in its own.
        doFirst { workingDir = projectDir }

        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
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
            register("neoforge", "deps.neoforge_compat")
            // Sodium finds the mod's pages through this property, on the targets that have them.
            put("sodium", if (project.findProperty("deps.sodium") != null)
                "[modproperties.${project.property("mod.id")}]\n\"sodium:config_api_user\" = \"fr.clixmods.livinghorizon.ui.SodiumOptions\"\n" else "")
            put("java", requiredJava.majorVersion)
            put("mixins", project.extra["lhMixins"] as String)
            // Only the Forge targets that run under obfuscated names need a refmap.
            put("refmap", "")
        }

        inputs.property("java", requiredJava.majorVersion)
        inputs.property("sodium", props.getValue("sodium"))
        inputs.property("mixins", project.extra["lhMixins"] as String)
        filesMatching(listOf("META-INF/neoforge.mods.toml", "livinghorizon.mixins.json", "assets/livinghorizon/version.txt")) { expand(props) }
        // The other loaders' metadata has nothing to say to NeoForge, and it reads its
        // access transformer, not an access widener.
        exclude("fabric.mod.json", "META-INF/mods.toml", "quilt.mod.json",
                "livinghorizon.accesswidener")
        from(lhAccessTransformer.parentFile.parentFile)
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
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
