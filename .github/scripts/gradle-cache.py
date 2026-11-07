#!/usr/bin/env python3
"""Prints the keys and paths of the CI's Gradle caches, as GITHUB_OUTPUT lines.

Usage: gradle-cache.py <node>, or gradle-cache.py shared for the shared entry alone.

Two kinds of entries, both written by the cache workflow on develop only (the pull
requests into develop can read what develop writes; what a pull request writes only
serves that pull request, and 56 of them evicted each other from the 10 GB GitHub
gives a repository):

- shared: the plugins and libraries Gradle downloads, the wrapper and the compiled build
  scripts, one entry for every job;
- one per Forge and NeoForge node: Minecraft decompiled, patched and recompiled, which
  takes those loaders three minutes per node and seconds once cached. Keeping Loom's for
  Fabric and Quilt saves about ten seconds, which is not worth the room.

What a job only downloads (the JDKs and the game files Forge fetches, the game's sounds
and textures NeoForge fetches) is left out: downloading it again is quicker than keeping it.
Except what Forge's Maven serves (its userdev and the tools it runs, a few MB per node):
that one server, asked by every Forge job at once, stalls or drops downloads.

A key is a hash of the files that decide the entry's content. The mod's identity and
version (the "mod" keys of stonecutter.properties.toml) are left out: the release and
the pull request build stamp a new version on every merge.
"""
import hashlib
import json
import sys
import tomllib
from pathlib import Path

# Raised to start every entry afresh, whatever the files say.
VERSION = "v1"

LOADERS = ("neoforge", "quilt", "forge")

# Directories are listed by what to keep: actions/cache archives a listed directory
# whole, whatever a "!" line says about what is inside it.
SHARED_PATHS = [
    "~/.gradle/caches/modules-2",
    "~/.gradle/caches/*/kotlin-dsl",
    "~/.gradle/wrapper/dists",
]

# Its other folders hold downloads: the JDKs it runs its tools on, the game files
# (minecraft/, from Mojang). maven/forge/ is what came from maven.minecraftforge.net.
FORGE_CACHE = "~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches"

NODE_PATHS = {
    "forge": [
        f"{FORGE_CACHE}/forge",
        f"{FORGE_CACHE}/mcp",
        f"{FORGE_CACHE}/maven/forge",
    ],
    "neoforge": [
        "~/.gradle/caches/neoformruntime/intermediate_results",
    ],
}

# Every build script: any of them may change what is downloaded or how it is compiled.
SCRIPTS = [
    "settings.gradle.kts",
    "stonecutter.gradle.kts",
    "build.gradle.kts",
    "build.quilt.gradle.kts",
    "build.neoforge.gradle.kts",
    "build.forge.gradle.kts",
    "gradle/target.gradle.kts",
    "gradle/wrapper/gradle-wrapper.properties",
]

# The access transformer is generated from it, and Forge and NeoForge apply it to the
# game before decompiling.
ACCESS = "src/main/resources/livinghorizon.accesswidener"


def digest(*parts: bytes) -> str:
    sha = hashlib.sha256()
    for part in parts:
        sha.update(hashlib.sha256(part).digest())
    return sha.hexdigest()[:20]


def read(path: str) -> bytes:
    return Path(path).read_bytes()


def without_mod(table: dict) -> bytes:
    return encode(strip(table))


def encode(value) -> bytes:
    return json.dumps(value, sort_keys=True).encode()


def strip(table: dict) -> dict:
    return {k: v for k, v in table.items() if k != "mod"}


def output(name: str, value) -> None:
    if isinstance(value, list):
        print(f"{name}<<EOF")
        print("\n".join(value))
        print("EOF")
    else:
        print(f"{name}={value}")


def main() -> None:
    node = sys.argv[1]
    config = tomllib.loads(read("stonecutter.properties.toml").decode())
    tables = {k: v for k, v in config.items() if k[:1].isdigit()}
    common = {k: v for k, v in config.items() if k not in tables}
    if node != "shared" and node not in tables:
        sys.exit(f"No table for {node} in stonecutter.properties.toml")

    shared = digest(
        without_mod(common),
        encode({k: strip(v) for k, v in tables.items()}),
        *(read(script) for script in SCRIPTS),
    )
    output("shared-key", f"gradle-shared-{VERSION}-{shared}")
    output("shared-restore", f"gradle-shared-{VERSION}-")
    output("shared-paths", SHARED_PATHS)

    if node == "shared":
        return

    loader = next((name for name in LOADERS if node.endswith("-" + name)), "fabric")
    if loader not in NODE_PATHS:
        output("node-key", "")
        return

    own = digest(
        without_mod(common),
        without_mod(tables[node]),
        read("settings.gradle.kts"),
        read("gradle/target.gradle.kts"),
        read(f"build.{loader}.gradle.kts"),
        read(ACCESS),
    )
    output("node-key", f"minecraft-{node}-{VERSION}-{own}")
    output("node-restore", f"minecraft-{node}-{VERSION}-")
    output("node-paths", NODE_PATHS[loader])


if __name__ == "__main__":
    main()
