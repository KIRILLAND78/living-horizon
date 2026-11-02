#!/usr/bin/env bash
#
# Runs ./gradlew with the CI's options, the tasks given as arguments:
#
#   gradle.sh :1.21.11:build :1.21.11:buildAndCollect
#
# Fifty-odd runners download the same plugins and Minecraft files at once, and the
# Maven repositories behind them (Forge's most of all) sometimes drop a download or
# leave it hanging with no end: ForgeGradle's Mavenizer waits on a stalled transfer
# with no time limit of its own. So an attempt that stalls is stopped, and one stopped
# that way or ended by a failed download is tried again, three times at most. Any other
# failure (a compile error, a test) ends it at once.
#
# An attempt has stalled when, for GRADLE_STALL_LIMIT seconds, nothing moved: no line in
# the log, no byte more in Gradle's folders, almost no CPU for Java. A healthy build is
# never that still: Loom setting up Minecraft prints nothing for five minutes but keeps
# the CPU busy or writes files, and a slow download still writes. An attempt that runs
# past GRADLE_ATTEMPT_LIMIT is stopped too, whatever it is doing.
#
# The Mavenizer writes a download straight into its cache: one cut short (stopped, or
# dropped by the server) stays there as a truncated jar, and the next attempt fails on
# it ("zip END header not found"). Before trying again, any jar or zip of its folders
# that does not open is deleted, so that it is downloaded again.
#
# The overall limit stays well above a slow but healthy build: Loom setting up Minecraft
# from nothing has taken up to 17 minutes when the repositories were slow.
set -uo pipefail

limit=${GRADLE_ATTEMPT_LIMIT:-25m}
stall_limit=${GRADLE_STALL_LIMIT:-300}
log="${RUNNER_TEMP:-/tmp}/gradle.log"
attempts=3
gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
tick_rate=$(getconf CLK_TCK)

# CPU time used so far by every Java process (Gradle, its daemon, the tools it runs), in
# clock ticks: ps only counts whole seconds, which idle JVMs cross now and then.
java_ticks() {
    local pid
    for pid in $(pgrep -x java); do cat "/proc/${pid}/stat" 2>/dev/null; done |
        awk '{ s += $14 + $15 } END { print s + 0 }'
}

# Bytes in the folders a build downloads and writes into. The daemons' own logs are left
# out: they may grow while the build waits on nothing.
written() {
    du -sb --exclude=daemon "$gradle_home" .gradle build versions 2>/dev/null |
        awk '{ s += $1 } END { print s + 0 }'
}

# Runs one attempt, its output shown as it comes and kept in $log. Stops it when it
# stalls (sets $stalled) or runs past $limit (timeout's 124).
run_attempt() {
    stalled=false
    : > "$log"
    timeout --kill-after=1m "$limit" ./gradlew "$@" --stacktrace --configure-on-demand \
        -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64,JAVA_HOME_25_X64 \
        > "$log" 2>&1 &
    local pid=$!
    tail -n +1 -f --pid="$pid" "$log" &
    local tail_pid=$!

    # What things looked like when the attempt last did something, and when that was.
    local since=$SECONDS lines bytes ticks quiet
    local ref_lines ref_bytes ref_ticks
    ref_lines=$(wc -l < "$log"); ref_bytes=$(written); ref_ticks=$(java_ticks)
    while kill -0 "$pid" 2>/dev/null; do
        sleep 10
        lines=$(wc -l < "$log"); bytes=$(written); ticks=$(java_ticks)
        quiet=$((SECONDS - since))
        # Idle JVMs use a little CPU (the collector, the compiler): under a tenth of a core
        # on average counts as still.
        if [ "$lines" -eq "$ref_lines" ] && [ "$bytes" -le "$ref_bytes" ] &&
            [ $((ticks - ref_ticks)) -lt $((quiet * tick_rate / 10)) ]; then
            if [ "$quiet" -ge "$stall_limit" ]; then
                stalled=true
                kill -TERM "$pid" 2>/dev/null
                break
            fi
        else
            since=$SECONDS; ref_lines=$lines; ref_bytes=$bytes; ref_ticks=$ticks
        fi
    done
    wait "$pid"
    local status=$?
    wait "$tail_pid" 2>/dev/null
    return "$status"
}

# Killing the client leaves the daemon and the tools it started running, holding the
# cache's locks: the next attempt would wait on them.
stop_gradle() {
    ./gradlew --stop >/dev/null 2>&1 || true
    pkill -f mavenizer || true
}

# Deletes the jars and zips of the Mavenizer's folders that do not open.
drop_partial() {
    python3 -I - "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/minecraftforge" .gradle/mavenizer <<'PY'
import sys, zipfile
from pathlib import Path
for root in map(Path, sys.argv[1:]):
    if not root.is_dir():
        continue
    for path in root.rglob("*"):
        if path.suffix not in (".jar", ".zip") or not path.is_file():
            continue
        try:
            zipfile.ZipFile(path).close()
        except (zipfile.BadZipFile, OSError):
            path.unlink()
            print(f"::warning::Deleted a download cut short: {path}")
PY
}

for attempt in $(seq 1 "$attempts"); do
    run_attempt "$@"
    status=$?
    [ "$status" -eq 0 ] && exit 0

    if [ "$stalled" = true ]; then
        echo "::warning::Gradle did nothing for ${stall_limit} s on attempt ${attempt} of ${attempts}, most likely on a stalled download."
        stop_gradle
    elif [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then
        echo "::warning::Gradle ran past ${limit} on attempt ${attempt} of ${attempts}, most likely on a stalled download."
        stop_gradle
    elif grep -qE "Could not (resolve|GET|HEAD|download)|could not resolve plugin artifact|Connection (reset|refused|timed out)|Read timed out|Remote host terminated|status code 5[0-9][0-9]|Returned 5[0-9][0-9]|RST_STREAM|IOException: closed|HttpTimeoutException|request timed out|zip END header not found" "$log"; then
        echo "::warning::A download failed on attempt ${attempt} of ${attempts}."
    else
        exit "$status"
    fi

    drop_partial
    [ "$attempt" -lt "$attempts" ] && sleep $((attempt * 30))
done

echo "::error::Gradle failed ${attempts} times on downloads; the Maven repositories may be down."
exit 1
