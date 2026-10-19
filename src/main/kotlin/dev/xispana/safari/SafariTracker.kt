package dev.xispana.safari

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object SafariTracker {

    var currentRun: SafariRun? = null
        private set

    /** Last finished run, so /csafari can still show a summary after you leave. */
    var lastRun: SafariRun? = null
        private set

    /** Set manually by /csafari start|stop; suppresses automatic detection. */
    private var manualOverride: Boolean = false

    private var wasInSafari: Boolean = false
    private var tickCounter: Int = 0

    /** Raw line -> last-seen timestamp, to catch Hypixel occasionally sending a line twice. */
    private val recentLines: LinkedHashMap<String, Long> = LinkedHashMap()
    private const val DEDUPE_WINDOW_MS = 2000L

    private var lastLevel: ClientLevel? = null

    /** 1-up count of every run started this session; hands each SafariRun its runNumber. */
    private var runCounter: Int = 0

    fun tick(mc: Minecraft) {
        if (mc.player == null) return

        // Every genuine instance/dimension switch constructs a new ClientLevel object, even
        // when the new instance looks identical to the old one (e.g. queued straight into a
        // fresh Critter Safari without ever seeing "not in safari" in between - the sidebar
        // text alone can't tell two consecutive instances apart, but this can). If we were
        // mid-run when that happens, force it to end here instead of silently continuing to
        // accumulate into what is now a stale run.
        val level = mc.level
        if (level !== lastLevel) {
            lastLevel = level
            if (currentRun != null) {
                end(announce = true)
                wasInSafari = false
            }
        }

        // Runs every tick, independent of manualOverride below, so the timer keeps counting
        // ticks (and scheduled party-chat posts still fire) during a manually started run.
        currentRun?.let { run ->
            run.tick()
            checkAnnouncements(run)
        }

        if (manualOverride) return

        // Sidebar only needs checking a few times a second.
        if (++tickCounter % 10 != 0) return

        val inSafari = LocationDetector.inSafari(mc)
        if (inSafari && !wasInSafari) {
            begin(announce = true)
        } else if (!inSafari && wasInSafari) {
            end(announce = true)
        }
        wasInSafari = inSafari
    }

    /**
     * The number a shiny gets in the session log, matching "Runs counted" in /csafari session.
     * run.runNumber (runCounter) goes up for EVERY run started, including false starts and
     * runs under a minute that SessionStats refuses to count - so shinies were being numbered
     * ahead of the counted total (e.g. "run 83" while only 68 runs were counted). Instead this is
     * the number this run will have among counted runs: the count so far, plus one if this run
     * hasn't been counted yet (it's counted when it ends, or when it clears every biome). A run
     * that already counted at its completion moment is already inside SessionStats.count.
     */
    private fun countedRunNumber(run: SafariRun): Int =
        if (run.sessionCounted) SessionStats.count else SessionStats.count + 1

    fun begin(announce: Boolean) {
        runCounter++
        currentRun = SafariRun(runCounter)
        if (announce) {
            send(Component.literal("Safari started - timer running.").withStyle(ChatFormatting.AQUA))
        }
    }

    fun end(announce: Boolean) {
        val run = currentRun ?: return
        run.pause()
        // Session stats previously only counted a run that cleared the full dex (all 4
        // biomes), recorded at the instant that happened - deliberately, so lingering
        // afterward couldn't inflate the time. But that meant a real, played-out run that
        // fell short (35/37, say) never counted at all. Now: if the run never hit that
        // completion moment, it still counts here, at whatever elapsed time it actually ran
        // for - still gated by SessionStats' own under-a-minute filter, so a flicker/false
        // detection still can't sneak in. run.sessionCounted stops this from double-counting
        // a run that already got recorded at its completion moment above.
        if (!run.sessionCounted) {
            SessionStats.recordCompletion(run.elapsedMs())
            run.markSessionCounted()
        }
        lastRun = run
        currentRun = null
        if (announce) printSummary(run, "Safari finished")
    }

    fun startManual() {
        manualOverride = true
        wasInSafari = true
        begin(announce = true)
    }

    fun stopManual() {
        end(announce = true)
        manualOverride = false
        wasInSafari = false
    }

    fun reset() {
        currentRun?.let {
            // Re-does the current run in place - same runNumber, not a new entry in the
            // session's run count or the shiny log's numbering.
            currentRun = SafariRun(it.runNumber)
            send(Component.literal("Run reset.").withStyle(ChatFormatting.YELLOW))
        }
    }

    /** For `/csafari session reset`: wipes this session's run count, averages, PB and shiny log. */
    fun resetSession() {
        SessionStats.reset()
        SessionShinyLog.reset()
        runCounter = 0
    }

    /**
     * Called from the cancellable ALLOW_GAME hook. Returns true to let the vanilla line
     * through unchanged, false to hide it (because we already sent our own version of it).
     *
     * A capture and a loot share are handled identically here - same "is this new" check,
     * same set, same biome-completion check. The only branch is which chat line goes out.
     */
    fun handleChatLine(rawLine: String): Boolean {
        val event = ChatParser.parse(rawLine) ?: return true // not a safari line, leave it alone
        val run = currentRun ?: return true // not tracking a run, nothing to replace it with

        if (isDuplicate(rawLine)) return false // Hypixel resent the same line; drop it silently

        return when (event) {
            is ChatParser.Event.Capture -> handleFound(run, event.critter, event.sparkling, source = null)
            is ChatParser.Event.LootShare -> handleFound(run, event.critter, event.sparkling, source = event.player)
            is ChatParser.Event.SparklingBroadcast -> {
                val isNewSparkling = event.critter !in run.sparkling
                run.onSparklingBroadcast(event.critter)
                run.contributors.add(event.player)
                if (isNewSparkling) SessionShinyLog.record(countedRunNumber(run), event.critter.name)
                true // keep the vanilla SPARKLING! broadcast line, per your request
            }
        }
    }

    /** Returns true to leave the vanilla chat line alone, false to replace it with our own
     *  compact version - per your request, a SPARKLING catch/share keeps its vanilla line
     *  (everything else - tracking, biome-complete announcements, party chat - is unaffected,
     *  this only decides whether OUR extra per-catch line also gets printed). */
    private fun handleFound(run: SafariRun, critter: Critter, sparkling: Boolean, source: String?): Boolean {
        val isNew = !run.found.contains(critter)
        // Checked before recording, since onCapture/onLootShare is what adds it to
        // run.sparkling - logged once per critter per run, whichever of capture/share/
        // broadcast marks it sparkling first.
        val isNewSparkling = sparkling && critter !in run.sparkling
        if (source == null) run.onCapture(critter, sparkling) else run.onLootShare(critter, sparkling)
        run.contributors.add(source ?: "You")
        if (isNewSparkling) SessionShinyLog.record(countedRunNumber(run), critter.name)
        if (!sparkling) announce(run, critter, sparkling, isNew, source)
        if (isNew && run.checkBiomeCompletion(critter.biome)) {
            SessionStats.recordBiome(critter.biome, run.elapsedMs())
            val isNewBest = BiomeBests.recordIfBest(critter.biome, run.elapsedMs(), source ?: "You")
            announceBiomeComplete(run, critter.biome, isNewBest)
            if (AnnounceConfig.completeToPartyChat) sendBiomeCompleteToPartyChat(run, critter.biome, isNewBest)

            if (run.checkAllBiomesComplete()) {
                SessionStats.recordCompletion(run.elapsedMs())
                run.markSessionCounted()
                // Credits every name that contributed a find this run (up to the whole
                // party), not just whoever happened to catch the one critter that tipped it
                // over to "complete" - a full-dex clear is a team effort.
                val credited = run.contributors.joinToString(", ")
                val isNewAllBest = BiomeBests.recordAllBiomesIfBest(run.elapsedMs(), credited)
                announceAllBiomesComplete(run, isNewAllBest)
                if (AnnounceConfig.completeToPartyChat) sendAllBiomesCompleteToPartyChat(run, isNewAllBest)
            }
        }
        return sparkling
    }

    private fun isDuplicate(rawLine: String): Boolean {
        val now = System.currentTimeMillis()
        recentLines.entries.removeIf { now - it.value > DEDUPE_WINDOW_MS }
        val last = recentLines.put(rawLine, now)
        return last != null && now - last <= DEDUPE_WINDOW_MS
    }

    private fun checkAnnouncements(run: SafariRun) {
        if (!AnnounceConfig.enabled || AnnounceConfig.marks.isEmpty()) return
        val elapsedSec = (run.elapsedMs() / 1000).toInt()
        for (mark in AnnounceConfig.marks) {
            if (elapsedSec >= mark && run.markAnnounced(mark)) {
                sendMissingToPartyChat(run)
            }
        }
    }

    private fun sendMissingToPartyChat(run: SafariRun) {
        val missing = run.missingByBiome()
        val parts = Biome.entries.mapNotNull { biome ->
            val list = missing[biome]
            if (list.isNullOrEmpty()) null else "${biome.display} ${list.joinToString(", ") { it.name }}"
        }
        if (parts.isEmpty()) return // full dex already, nothing to post
        // connection.sendCommand sends exactly what typing "/pc <text>" and hitting enter
        // would - it goes straight to the server, unlike sendSystemMessage which is local-only.
        Minecraft.getInstance().player?.connection?.sendCommand("pc " + parts.joinToString(" | "))
    }

    fun printBests() {
        send(Component.literal("--- Personal bests ---").withStyle(ChatFormatting.AQUA))
        for (biome in Biome.entries) {
            val record = BiomeBests.best(biome)
            val value = if (record != null) "${SafariRun.formatTime(record.timeMs)} - ${record.player}" else "Not yet completed"
            send(line(biome.display, value))
        }
        val allRecord = BiomeBests.allBiomesBest()
        val allValue = if (allRecord != null) "${SafariRun.formatTime(allRecord.timeMs)} - ${allRecord.player}" else "Not yet completed"
        send(line("All biomes", allValue))
    }

    fun printAnnounceMarks() {
        if (AnnounceConfig.marks.isEmpty()) {
            send(Component.literal("No announce times set.").withStyle(ChatFormatting.GRAY))
            return
        }
        send(
            Component.literal("Announce at: ").withStyle(ChatFormatting.GRAY)
                .append(
                    Component.literal(AnnounceConfig.marks.sorted().joinToString(", ") { "${it}s" })
                        .withStyle(ChatFormatting.WHITE)
                )
        )
    }

    /** source == null means you caught it yourself; otherwise it's the teammate's name. */
    private fun announce(run: SafariRun, critter: Critter, sparkling: Boolean, isNew: Boolean, source: String?) {
        val prefix = if (sparkling) "SPARKLING " else ""
        val nameStyle = if (sparkling) ChatFormatting.GOLD else ChatFormatting.WHITE

        // A LOOT SHARE line always names a player, so it can never be your own catch - that
        // check has to win over isNew, or a teammate's first-of-the-run share still shows the
        // "New:" prefix with only a small trailing name tag as the (easy to miss) giveaway.
        // A new shared find used to reuse the bright New: green here - fixed to dark green,
        // same as every other shared line, without changing anything else about the format.
        val message: MutableComponent = when {
            source != null && isNew -> Component.literal("New (shared): ").withStyle(ChatFormatting.DARK_GREEN)
            source != null -> Component.literal("Shared: ").withStyle(ChatFormatting.DARK_GREEN)
            isNew -> Component.literal("New: ").withStyle(ChatFormatting.GREEN)
            else -> Component.literal("Caught: ").withStyle(ChatFormatting.GRAY)
        }
        message.append(Component.literal("$prefix${critter.name}").withStyle(nameStyle))

        if (isNew) {
            message.append(
                Component.literal("  (${run.found.size}/${Critters.TOTAL} at ${SafariRun.formatTime(run.elapsedMs())})")
                    .withStyle(ChatFormatting.GRAY)
            )
        } else {
            message.append(Component.literal(" (already found)").withStyle(ChatFormatting.DARK_GRAY))
        }

        if (source != null) {
            message.append(Component.literal("  - $source").withStyle(ChatFormatting.GRAY))
        }

        send(message)
    }

    private fun announceBiomeComplete(run: SafariRun, biome: Biome, isNewBest: Boolean) {
        val message = Component.literal("\u2605 ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("${biome.display} biome complete!").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("  (${SafariRun.formatTime(run.elapsedMs())})").withStyle(ChatFormatting.GRAY))

        if (isNewBest) {
            message.append(Component.literal("  New PB!").withStyle(ChatFormatting.LIGHT_PURPLE))
        } else {
            BiomeBests.best(biome)?.let {
                message.append(
                    Component.literal("  (Best: ${SafariRun.formatTime(it.timeMs)} - ${it.player})")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
            }
        }
        send(message)
    }

    private fun sendBiomeCompleteToPartyChat(run: SafariRun, biome: Biome, isNewBest: Boolean) {
        val time = SafariRun.formatTime(run.elapsedMs())
        val suffix = if (isNewBest) {
            " (NEW PB!)"
        } else {
            BiomeBests.best(biome)?.let { " (PB: ${SafariRun.formatTime(it.timeMs)})" } ?: ""
        }
        Minecraft.getInstance().player?.connection?.sendCommand("pc ${biome.display} biome complete! $time$suffix")
    }

    private fun announceAllBiomesComplete(run: SafariRun, isNewBest: Boolean) {
        val message = Component.literal("\u2605\u2605 ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("ALL biomes complete!").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("  (${SafariRun.formatTime(run.elapsedMs())})").withStyle(ChatFormatting.GRAY))

        if (isNewBest) {
            message.append(Component.literal("  New PB!").withStyle(ChatFormatting.LIGHT_PURPLE))
        } else {
            BiomeBests.allBiomesBest()?.let {
                message.append(
                    Component.literal("  (Best: ${SafariRun.formatTime(it.timeMs)} - ${it.player})")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
            }
        }
        send(message)
    }

    private fun sendAllBiomesCompleteToPartyChat(run: SafariRun, isNewBest: Boolean) {
        val time = SafariRun.formatTime(run.elapsedMs())
        val suffix = if (isNewBest) {
            " (NEW PB!)"
        } else {
            BiomeBests.allBiomesBest()?.let { " (PB: ${SafariRun.formatTime(it.timeMs)})" } ?: ""
        }
        Minecraft.getInstance().player?.connection?.sendCommand("pc ALL biomes complete! $time$suffix")
    }

    fun printSummary(run: SafariRun, title: String) {
        send(Component.literal("--- $title ---").withStyle(ChatFormatting.AQUA))
        send(line("Time", SafariRun.formatTime(run.elapsedMs())))
        send(line("Uniques", "${run.found.size}/${Critters.TOTAL}"))
        for (stat in run.biomeStats()) {
            send(line("  ${stat.biome.display}", "${stat.found}/${stat.total}"))
        }
        if (run.sparkling.isNotEmpty()) {
            send(line("Sparkling", run.sparkling.joinToString(", ") { it.name }))
        }
        SessionStats.averageMs?.let {
            send(line("Session avg", "${SafariRun.formatTime(it)} (${SessionStats.count} runs)"))
        }
        SessionStats.pbElapsedMs?.let {
            send(line("Session PB", SafariRun.formatTime(it)))
        }
    }

    fun printSessionStats() {
        if (SessionStats.count == 0) {
            send(
                Component.literal("No runs counted this session yet (runs under a minute don't count).")
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }
        send(Component.literal("--- This session ---").withStyle(ChatFormatting.AQUA))
        send(line("Runs counted", SessionStats.count.toString()))
        send(line("Average time", SafariRun.formatTime(SessionStats.averageMs!!)))
        send(line("PB time", SafariRun.formatTime(SessionStats.pbElapsedMs!!)))
        // Per-biome averages, fixed biome order. A biome that never finished this session shows
        // "-" rather than disappearing, so the four lines always line up.
        for (biome in Biome.entries) {
            val avg = SessionStats.biomeAverageMs(biome)
            val value = if (avg == null) "-" else "${SafariRun.formatTime(avg)} (${SessionStats.biomeRuns(biome)} runs)"
            send(line("${biome.display} avg", value))
        }
        printShinyLog()
    }

    private fun printShinyLog() {
        val shinies = SessionShinyLog.all
        if (shinies.isEmpty()) {
            send(line("Shinies", "None yet"))
            return
        }
        send(
            Component.literal("Shinies: ").withStyle(ChatFormatting.GRAY)
                .append(
                    Component.literal(
                        shinies.joinToString(", ") { "run ${it.runNumber} ${it.critterName}" }
                    ).withStyle(ChatFormatting.GOLD)
                )
        )
    }

    fun printMissing(run: SafariRun) {
        val missing = run.missingByBiome()
        if (run.missing().isEmpty()) {
            send(Component.literal("Full dex this run.").withStyle(ChatFormatting.GREEN))
            return
        }
        send(Component.literal("--- Missing (${run.missing().size}) ---").withStyle(ChatFormatting.AQUA))

        val combined = Component.literal("")
        var first = true
        for (biome in Biome.entries) {
            val list = missing[biome] ?: continue
            if (!first) combined.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY))
            combined.append(Component.literal("${biome.display}: ").withStyle(ChatFormatting.YELLOW))
            combined.append(
                Component.literal(list.joinToString(", ") { it.name }).withStyle(ChatFormatting.WHITE)
            )
            first = false
        }
        send(combined)
    }

    private fun line(label: String, value: String): Component =
        Component.literal("$label: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE))

    private fun send(component: Component) {
        // Player.displayClientMessage was removed in 26.1.2; sendSystemMessage replaced it.
        Minecraft.getInstance().player?.sendSystemMessage(component)
    }
}
