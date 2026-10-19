package dev.xispana.safari

/**
 * Session-only (not persisted - resets every game launch, or on demand via
 * `/csafari session reset`) log of every Sparkling critter seen this session, tagged with
 * which numbered run it happened on. The number follows the "Runs counted" total (runs under a
 * minute excluded, same as SessionStats) via SafariTracker.countedRunNumber - NOT the raw
 * SafariRun.runNumber, which counts every run started including false starts.
 *
 * Logged once per critter per run - the first time that critter is marked sparkling this run,
 * whichever of the three ways that happens first: your own capture, a teammate's loot share,
 * or the SPARKLING broadcast line. A critter re-announced later the same run (e.g. a loot
 * share confirming something you already caught) doesn't add a second entry.
 */
object SessionShinyLog {

    data class Sighting(val runNumber: Int, val critterName: String)

    private val entries = mutableListOf<Sighting>()

    fun record(runNumber: Int, critterName: String) {
        entries.add(Sighting(runNumber, critterName))
    }

    val all: List<Sighting> get() = entries.toList()

    fun reset() {
        entries.clear()
    }
}
