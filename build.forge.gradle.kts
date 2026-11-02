import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// The Forge targets' build script: the same sources as build.gradle.kts, built by
// ForgeGradle 7 instead of Loom. What does not depend on the loader - the Java level,
// the tests, the jar name - is kept word for word the same as
// there, so a change to one of those belongs in all three files.
plugins {
    id("net.minecraftforge.gradle")
    // ForgeGradle 7 leaves Jar-in-Jar to Forge's separate plugin.
    id("net.minecraftforge.jarjar")
    // And reobfuscation to another: see `obfuscatedRuntime` below.
    id("net.minecraftforge.renamer")
}

// The loader is in the file name and not in the version, like on NeoForge: the Fabric
// release globs match "+mc<version>.jar", and a Forge jar must not answer to them.
// livinghorizon-0.1.0+mc1.21.11-forge.jar
version = "${property("mod.version")}+mc${sc.current.version}-forge"
base.archivesName = property("mod.id") as String

// What the targets share, worked out once: the Java level, the access transformer, and the
// list of mixins. See gradle/target.gradle.kts.
extra["lhGame"] = sc.current.version
extra["lhUnobfuscated"] = sc.current.parsed >= "26.1"
apply(from = rootProject.file("gradle/target.gradle.kts"))
val requiredJava: JavaVersion = project.extra["lhJava"] as JavaVersion

// Forge ships Sponge's Mixin 0.8.7, whose compatibility levels stop at JAVA_21: it has
// no JAVA_25 to read. Java 25 mixin classes only make it log a warning - the game's own
// classes are Java 25 on 26.x - so the level is capped, not the bytecode.
// Forge before 52 (1.21) ships Mixin 0.8.5, which stops at JAVA_17.
val mixinJava: String = minOf(requiredJava.majorVersion.toInt(), if (sc.current.parsed < "1.21") 17 else 21).toString()

val lhAccessTransformer: File = project.extra["lhAccessTransformer"] as File

minecraft {
    accessTransformers.from(lhAccessTransformer)

    // 1.21.x still ships obfuscated; 26.x is unobfuscated and takes no mappings at all.
    // Forge runs the game under Mojang's names on both, which are the names the sources
    // are written in, so the jar needs no remapping.
    if (sc.current.parsed < "26.1") {
        mappings("official", sc.current.version)
    }

    runs {
        register("client") {
            workingDir.set(rootProject.layout.projectDirectory.dir("run"))
            // The jar names its mixin config in its manifest; a dev run reads the mod from
            // folders, which have none, so the config is named here.
            args("--mixin.config", "livinghorizon.mixins.json")
            // Forge before 49 (1.20.4) finds the mods of a dev run only where this names them.
            if (sc.current.parsed < "1.20.4") {
                environment.put("MOD_CLASSES", "livinghorizon%%" + sourceSets.main.get().java.destinationDirectory.get().asFile.absolutePath)
            }
        }
    }
}

// Forge takes a mod from one place: in a dev run, a classes folder and a resources folder
// side by side are two places, and the one with mods.toml has no classes in it. The
// resources go next to the classes, so the run sees the mod as it sees the jar.
sourceSets.main {
    output.setResourcesDir(java.destinationDirectory)
}

repositories {
    // Where ForgeGradle puts the game and Forge once it has set them up, and where
    // their libraries come from.
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

// The jar that ships is jarJar's: it takes the plain jar's name, and the plain jar steps
// aside as "-slim".
jarJar.register {
    archiveClassifier = null
}

// Forge runs the game under Mojang's names from 1.20.6 on, and under its own obfuscated
// SRG names before. The sources are written in Mojang's names on every target, so on an
// older Forge the jar is renamed to SRG before it ships, and the mixins carry a refmap
// that tells Mixin the SRG name of every method they name - which nothing else needs,
// since Loom rewrites those annotations itself and NeoForge and the newer Forge run
// under the names the annotations already use.
val obfuscatedRuntime = sc.current.parsed < "1.20.5"
// Written into the mixin config on those targets only, for the same reason.
val refmap = "livinghorizon.refmap.json"
// The task whose jar is the one that ships: jarJar's, renamed where Forge needs it.
val shippedJar: String = if (obfuscatedRuntime) "renameJarJar" else "jarJar"
// Forge before 1.20.4 reads a mod's resources - its translations among them - only if
// the jar carries a pack.mcmeta, and logs "Missing metadata in pack" otherwise; from
// 1.20.4 Forge writes that metadata itself. Declared in the TOML where it is needed.
val packFormat = findProperty("mod.pack_format")?.toString()

dependencies {
    // The code is annotated with jspecify's @Nullable, which the game ships from 1.21.11 on.
    // Before that it is only needed to compile: an annotation nobody reads at run time.
    if (sc.current.parsed < "1.21.11") compileOnly("org.jspecify:jspecify:1.0.0")
    // The game and Forge together. implementation also puts them on the test
    // classpath, where the tests read Component and PlayerModelType, like on the other
    // loaders.
    implementation(minecraft.dependency("net.minecraftforge:forge:${sc.current.version}-${property("deps.forge")}"))

    // MixinExtras, which some mixins use, ships with Forge from 60 (1.21.10) on. Before,
    // the mod carries it in its jar, where Forge loads it like a mod of its own.
    if (sc.current.parsed < "1.21.10") {
        val mixinExtras = "io.github.llamalad7:mixinextras-forge:${property("deps.mixinextras")}"
        compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${property("deps.mixinextras")}")!!)
        implementation(mixinExtras)
        "jarJar"(mixinExtras)
    }

    testImplementation(platform("org.junit:junit-bom:${property("deps.junit")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// After the dependencies: the SRG mappings are the Minecraft dependency's, and asking
// for them before it is declared fails the whole configuration.
if (obfuscatedRuntime) {
    renamer.mappings(minecraft.dependency.toSrg)
    val mixin = renamer.enableMixinRefmaps {
        refMap.set(refmap)
        config("livinghorizon.mixins.json")
    }
    // jarJar copies the plain jar, which Renamer has given the refmap; the copy is then
    // renamed, the mixins' own extra names included.
    renamer.classes(tasks.named<Jar>("jarJar")) {
        mappings(mixin.generatedMappings)
        output.set(layout.buildDirectory.file("libs/srg/${base.archivesName.get()}-$version.jar"))
    }
    dependencies {
        annotationProcessor("org.spongepowered:mixin:0.8.7:processor")
    }
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
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()

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
            register("forge", "deps.forge_compat")
            put("java", mixinJava)
            put("mixins", project.extra["lhMixins"] as String)
            put("refmap", if (obfuscatedRuntime) "\"refmap\": \"$refmap\"," else "")
        }

        inputs.property("java", mixinJava)
        inputs.property("mixins", project.extra["lhMixins"] as String)
        inputs.property("obfuscatedRuntime", obfuscatedRuntime)
        filesMatching(listOf("META-INF/mods.toml", "livinghorizon.mixins.json", "assets/livinghorizon/version.txt")) { expand(props) }
        // The other loaders' metadata has nothing to say to Forge, and it reads its
        // access transformer, not an access widener.
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "quilt.mod.json",
                "livinghorizon.accesswidener")
        from(lhAccessTransformer.parentFile.parentFile)

        if (packFormat != null) {
            val description: String = sc.properties["mod.name"]
            inputs.property("packFormat", packFormat)
            val output = destinationDir
            doLast {
                output.resolve("pack.mcmeta").writeText(
                    "{\"pack\": {\"description\": \"$description\", \"pack_format\": $packFormat}}\n")
            }
        }
    }

    jar {
        archiveClassifier = "slim"
    }

    withType<Jar>().configureEach {
        // Forge reads mixin configs from the manifest rather than from mods.toml. Set
        // on the jarJar task as well as the plain jar, so the jar that ships has it
        // whether or not jarJar copies the plain jar's manifest.
        manifest.attributes("MixinConfigs" to "livinghorizon.mixins.json")
    }

    // Not the jarJar task: it already carries everything the plain jar does, and a
    // second LICENSE would be a duplicate entry.
    withType<Jar>().matching { it.name != "jarJar" }.configureEach {
        val id = project.property("mod.id")
        inputs.property("mod_id", id)
        from(rootProject.file("LICENSE")) { rename { "${it}_$id" } }
        // The changelog the game shows in its own screen.
        from(rootProject.file("changelog")) { include("en.md", "fr.md"); into("assets/livinghorizon/changelog") }
    }

    named("assemble") { dependsOn(shippedJar) }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"

        inputs.property("version", project.property("mod.version"))
        // The jarJar task's output - never the -slim jar - and renamed
        // to SRG where Forge runs under those names.
        from(named(shippedJar))
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
