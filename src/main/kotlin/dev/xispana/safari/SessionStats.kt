package dev.xispana.safari

/**
 * Session-only stats (not persisted - resets every game launch) counting every real run you
 * play out, not just the ones where you caught all 37 critters.
 *
 * A full-dex clear is recorded at the instant all 4 biomes finish (its elapsed time at that
 * exact moment) rather than whenever the instance later ends - lingering around after
 * finishing shouldn't inflate that run's time. Any other run - however many critters it ended
 * with, 35/37 included - is instead recorded when it ends, using however long it actually
 * ran. Either way, a run only counts once (SafariTracker/SafariRun guard against double
 * counting the same run both ways).
 *
 * Runs under a minute are excluded entirely. Critter Safari instances run for a fixed length,
 * so anything detected as "finished" in under 60 seconds is almost certainly a false
 * detection (a sidebar flicker, a stale run that never properly reset, etc.) rather than a
 * genuine run - counting it would silently drag the average down with a data point that was
 * never real.
 */
object SessionStats {

    private const val MIN_VALID_MS = 60_000L

    private var completionCount: Int = 0
    private var totalCompletionMs: Long = 0L

    var pbElapsedMs: Long? = null
        private set

    /**
     * Call once per run - either the moment it clears all 4 biomes, or whenever it ends if it
     * never did - passing the elapsed time at that moment. Silently ignored if under a minute.
     */
    fun recordCompletion(elapsedMs: Long) {
        if (elapsedMs < MIN_VALID_MS) return
        completionCount++
        totalCompletionMs += elapsedMs
        if (pbElapsedMs == null || elapsedMs < pbElapsedMs!!) {
            pbElapsedMs = elapsedMs
        }
    }

    // Per-biome session tallies: how long each biome took to finish (from the start of the run to
    // the moment its last required critter was found), averaged over every run this session where
    // that biome finished. No 60-second floor like recordCompletion has - a single biome can
    // genuinely finish in under a minute, and it only ever records on a real catch anyway.
    private val biomeCount: MutableMap<Biome, Int> = mutableMapOf()
    private val biomeTotalMs: MutableMap<Biome, Long> = mutableMapOf()

    /** Call once per run per biome, at the moment that biome completes, with the run's elapsed time. */
    fun recordBiome(biome: Biome, elapsedMs: Long) {
        biomeCount[biome] = (biomeCount[biome] ?: 0) + 1
        biomeTotalMs[biome] = (biomeTotalMs[biome] ?: 0L) + elapsedMs
    }

    /** Average completion time of [biome] this session in ms, or null if it never finished one. */
    fun biomeAverageMs(biome: Biome): Long? {
        val n = biomeCount[biome] ?: return null
        return if (n == 0) null else (biomeTotalMs[biome] ?: 0L) / n
    }

    /** How many runs this session [biome] was completed in. */
    fun biomeRuns(biome: Biome): Int = biomeCount[biome] ?: 0

    val count: Int get() = completionCount

    /** Average completion time in ms across counted completions, or null if none counted yet. */
    val averageMs: Long?
        get() = if (completionCount == 0) null else totalCompletionMs / completionCount

    /** For `/csafari session reset`: wipes this session's stats back to zero. */
    fun reset() {
        completionCount = 0
        totalCompletionMs = 0L
        pbElapsedMs = null
        biomeCount.clear()
        biomeTotalMs.clear()
    }
}
