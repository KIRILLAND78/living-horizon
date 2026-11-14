// What every target works out the same way, whichever loader builds it, so that the four
// build scripts only say what is theirs. Applied by each of them after it has set
// `extra["lhGame"]` to the game version it builds: the Java level, the access widener and
// access transformer, and the list of mixins.
//
// Everything is written into the build folder at configuration time; the scripts and the
// tests read it from there.
val game = extra["lhGame"] as String
val unobfuscated = extra["lhUnobfuscated"] as Boolean

// "26.1.2" against "26.1": numeric, part by part, a missing part being zero.
fun atLeast(version: String): Boolean {
    val a = game.split(".").map { it.toInt() }
    val b = version.split(".").map { it.toInt() }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return true
}
fun atMost(version: String): Boolean = !atLeast(version) || game == version

// Mojang's requirement per game version, not a preference of ours.
extra["lhJava"] = when {
    atLeast("26.1") -> JavaVersion.VERSION_25
    atLeast("1.20.5") -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// The kinds of mob the mod shares (MobKinds.TYPES) that this game version does not have yet,
// for the tests: they check every other one exists.
run {
    val kinds = rootProject.file("src/main/java/fr/clixmods/livinghorizon/track/MobKinds.java").readText()
    val list = kinds.substringAfter("TYPES = List.of(").substringBefore(");")
    val types = Regex("\"(minecraft:[a-z_]+)\"").findAll(list).map { it.groupValues[1] }.toList()
    fun since(type: String): String? = when {
        type == "minecraft:happy_ghast" -> "1.21.6"
        type.startsWith("minecraft:pale_oak_") -> "1.21.4"
        // One entity type per wood from 1.21.2; before, a single boat with a variant.
        type.endsWith("_boat") || type.endsWith("_raft") -> "1.21.2"
        type == "minecraft:armadillo" -> "1.20.5"
        else -> null
    }
    val missing = types.filter { type -> since(type)?.let { !atLeast(it) } ?: false }
    tasks.withType<Test>().configureEach {
        systemProperty("livinghorizon.missingTypes", missing.joinToString(","))
    }
}

// The blocks of the access widener's source file that exist on this game version: a block
// whose first line is "# @since X" and/or "# @until X" is kept only on the versions it names,
// both ends included. Blocks are separated by a blank line.
val widenerBlocks: List<String> = run {
    val since = Regex("""^# @since (\S+)""")
    val until = Regex("""^# @until (\S+)""")
    rootProject.file("src/main/resources/livinghorizon.accesswidener").readText()
        .split(Regex("\n\\s*\n")).filter { block ->
            val lines = block.lines()
            val from = lines.firstNotNullOfOrNull { since.find(it)?.groupValues?.get(1) }
            val to = lines.firstNotNullOfOrNull { until.find(it)?.groupValues?.get(1) }
            (from == null || atLeast(from)) && (to == null || atMost(to))
        }
}

// The access widener Loom reads (Fabric and Quilt): its namespace is named, or official
// where the game is not obfuscated.
run {
    val file: File = layout.buildDirectory.file("generated/accesswidener/livinghorizon.accesswidener").get().asFile
    file.parentFile.mkdirs()
    file.writeText(widenerBlocks.joinToString("\n\n")
        .replace("\${namespace}", if (unobfuscated) "official" else "named").trimEnd() + "\n")
    extra["lhAccessWidener"] = file
}

// The access transformer NeoForge and Forge read: their format for the same thing, in the
// names of the game they compile against, which are Mojang's.
run {
    val file: File = layout.buildDirectory.file("generated/accesstransformer/META-INF/accesstransformer.cfg").get().asFile
    val lines = widenerBlocks.flatMap { it.lines() }.mapNotNull { line ->
        val part = line.trim().split(" ")
        fun name(internal: String) = internal.replace('/', '.')
        when {
            part.size == 3 && part[0] == "accessible" && part[1] == "class" -> "public ${name(part[2])}"
            part.size == 5 && part[0] == "accessible" && part[1] == "field" -> "public ${name(part[2])} ${part[3]}"
            part.size == 5 && part[0] == "accessible" && part[1] == "method" -> "public ${name(part[2])} ${part[3]}${part[4]}"
            else -> null
        }
    }
    file.parentFile.mkdirs()
    file.writeText(lines.joinToString("\n") + "\n")
    extra["lhAccessTransformer"] = file
}

// The changelog the game shows in its own screen: changelog/<lang>.md, one per language of
// changelog/languages.json, with the entries waiting in changelog/unreleased/ put under
// "Unreleased", as the release will date them.
// .github/scripts/changelog-fragments.js does the same for the release: keep the two alike.
run {
    val parts = listOf("server", "client")
    val kinds = listOf("new", "improved", "fixed")
    @Suppress("UNCHECKED_CAST")
    val languages = groovy.json.JsonSlurper().parseText(rootProject.file("changelog/languages.json").readText()) as Map<String, Map<String, Any>>
    data class Entry(val part: String, val kind: String, val text: String)

    // One file per entry, sorted by name: "part:", "kind:", then one field per language; a
    // line naming no field carries the one above on.
    val fields = setOf("part", "kind") + languages.keys
    val fragments: List<Map<String, String>> = (rootProject.file("changelog/unreleased").listFiles() ?: emptyArray())
        .filter { it.name.endsWith(".md") && it.name != "README.md" }.sortedBy { it.name }
        .map { file ->
            val read = linkedMapOf<String, String>()
            var current: String? = null
            for (line in file.readLines().map { it.trim() }.filter { it.isNotEmpty() }) {
                val field = Regex("""^([a-z_]+):\s*(.*)$""").find(line)?.takeIf { it.groupValues[1] in fields }
                if (field != null) {
                    current = field.groupValues[1]
                    read[current] = field.groupValues[2]
                } else if (current != null && current in languages) {
                    read[current] = (read[current]!! + " " + line).trim()
                }
            }
            read
        }

    val dir: File = layout.buildDirectory.dir("generated/changelog").get().asFile
    dir.mkdirs()
    for ((lang, words) in languages) {
        @Suppress("UNCHECKED_CAST") val areas = words.getValue("parts") as List<String>
        @Suppress("UNCHECKED_CAST") val headings = words.getValue("kinds") as List<String>
        val source = rootProject.file("changelog/$lang.md")
        if (!source.exists()) continue
        val text = source.readText().replace("\r\n", "\n")
        val open = Regex("^## " + Regex.escape(words.getValue("unreleased") as String) + "[ \t]*$", RegexOption.MULTILINE).find(text)
        if (open == null) {
            dir.resolve("$lang.md").writeText(text)
            continue
        }
        val start = open.range.last + 1
        val next = Regex("^## ", RegexOption.MULTILINE).find(text, start)
        val end = next?.range?.first ?: text.length

        // The bullets written straight under Unreleased, then the files.
        val entries = mutableListOf<Entry>()
        var part = "client"
        var kind = "new"
        for (line in text.substring(start, end).lines().map { it.trim() }) {
            when {
                line.startsWith("### ") -> part = parts.getOrElse(areas.indexOf(line.substring(4).trim())) { "client" }
                line.startsWith("#### ") -> kind = kinds.getOrElse(headings.indexOf(line.substring(5).trim())) { "new" }
                line.startsWith("- ") -> entries += Entry(part, kind, line.substring(2).trim())
                line.isNotEmpty() && entries.isNotEmpty() -> entries[entries.size - 1] = entries.last().let { it.copy(text = it.text + " " + line) }
            }
        }
        fragments.forEach { entries += Entry(it["part"] ?: "client", it["kind"] ?: "new", it[lang] ?: "") }

        val body = buildList {
            parts.forEachIndexed { p, partName ->
                val ofPart = entries.filter { it.part == partName }
                if (ofPart.isEmpty()) return@forEachIndexed
                add("### " + areas[p]); add("")
                kinds.forEachIndexed { k, kindName ->
                    val ofKind = ofPart.filter { it.kind == kindName }
                    if (ofKind.isEmpty()) return@forEachIndexed
                    add("#### " + headings[k]); ofKind.forEach { add("- " + it.text) }; add("")
                }
            }
        }.joinToString("\n")
        val rest = text.substring(end)
        dir.resolve("$lang.md").writeText(text.substring(0, start) + "\n\n" + body + (if (body.isEmpty() || rest.isEmpty()) "" else "\n") + rest)
    }
    extra["lhChangelog"] = dir
}

// The mixins this game version has something to hook into, written into the mixin config.
extra["lhMixins"] = buildList {
    // The F3 screen's entries a mod can add came in 1.21.9.
    if (atLeast("1.21.9")) {
        add("DebugScreenEntriesMixin")
        add("DebugScreenEntryListMixin")
    }
    // Distant Horizons' fade, which would paint its terrain over the distant figures.
    add("DhClientApiMixin")
    add("EntityMixin")
    add("EntityRenderDispatcherMixin")
    // The light of the figures the mod extracts, where the client has none.
    add("EntityRendererLightMixin")
    // The far plane: the game renderer's before 26.1, the camera's from there.
    add(if (atLeast("26.1")) "CameraMixin" else "GameRendererMixin")
    // From 26.1 the projection with view bobbing in it only goes to the GPU: read on its way.
    if (atLeast("26.1")) add("GameRendererProjectionMixin")
    add("GuiMixin")
    // The feature renderers came in 1.21.9.
    if (atLeast("1.21.9")) {
        add("FeatureRenderDispatcherMixin")
        add("ModelFeatureRendererMixin")
    }
    // The GUI pass the impostors are baked in came in 1.21.6. Before, they are baked into a
    // target of our own that stands in for the game's main one and its item target.
    if (atLeast("1.21.6")) {
        // Voxy's fog on the entities: the overworld's own fog past the render distance came in 1.21.6.
        add("FogRendererMixin")
        add("GuiRendererMixin")
    } else {
        add("LevelRendererTargetMixin")
        add("MinecraftMixin")
    }
    add("LevelRendererMixin")
    // The top of the game's loop, once a frame: the benchmark's frame times.
    add("MinecraftFrameMixin")
    // Before 1.21.2 a figure sits only when it rides: the render states carry the pose after.
    if (!atLeast("1.21.2")) add("LivingEntityRendererMixin")
    // The world's extraction left the level renderer for a class of its own in 26.2.
    if (atLeast("26.2")) add("LevelExtractorMixin")
    add("ModelPartMixin")
    // Before 1.20.5 the mod's screens have a list of their own, which places its rows itself.
    if (atLeast("1.20.5")) add("OptionsListEntryMixin")
    // "Create" on the world creation screen, for the benchmark's world.
    add("CreateWorldScreenAccessor")
    add("TextureManagerMixin")
    add("VoxyRenderSystemMixin")
}.sorted().joinToString(",\n    ") { "\"$it\"" }
