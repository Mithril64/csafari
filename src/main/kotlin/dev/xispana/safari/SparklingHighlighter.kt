package dev.xispana.safari

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * Scans nearby entities for Sparkling critters, two different ways, since neither alone is
 * reliable:
 *
 * 1. Nametag text containing "SPARKLING" - works for most of them, cheap and exact once it
 *    matches, but per your report some Sparkling critters don't actually have that word in
 *    their nametag at all.
 * 2. For critters #1 misses: resolve the entity's plain name (no "SPARKLING" needed) via
 *    Critters.matchAtStart, then check ParticleTracker for a shiny-looking particle recorded
 *    near that entity very recently (the cross/glint effect in your screenshot). This is the
 *    fallback for exactly the case you described - "Honeybug" with no "SPARKLING" text but the
 *    sparkle visual still playing around it.
 *
 * SHINY_PARTICLE_HINTS below used to be a best-effort guess list (totem_of_undying, end_rod,
 * firework, witch, enchant, crit) - too broad, and per your report `end_rod` was false-
 * flagging on unrelated particles. Replaced with the real id you confirmed via
 * `/csafari particles`: `wax_on` (the same particle vanilla uses for an axe un-waxing copper -
 * reused here, evidently, for the shiny glint).
 *
 * Scoped to an active safari run (Sparkling critters only exist there) and throttled to every
 * 5 ticks (4 scans/sec) - a full nearby-entity scan every single tick isn't needed for
 * something that only has to feel responsive, not instant.
 */
object SparklingHighlighter {

    var enabled: Boolean = true

    private const val MATCH = "sparkling"
    private const val SCAN_INTERVAL_TICKS = 5
    private var tickCounter = 0

    // Confirmed via /csafari particles - the only particle id the shiny effect actually uses.
    val SHINY_PARTICLE_HINTS: Set<String> = setOf("wax_on")
    private const val PARTICLE_RADIUS = 2.5
    private const val PARTICLE_MAX_AGE_MS = 1500L

    @Volatile
    var nearby: Boolean = false
        private set

    /** Display names of the currently-matched Sparkling critter(s), for the on-screen label. */
    @Volatile
    var names: Set<String> = emptySet()
        private set

    // Entity ids matched on the previous scan, so the sound only fires when a NEW critter
    // starts matching rather than retriggering every scan while one stays in range.
    private var previouslyMatched: Set<Int> = emptySet()

    // Sound is decided per CRITTER, not per entity id - a failed catch respawns the Sparkling as a
    // brand new entity, and the catch animation spawns ANOTHER one (the critter inside the ball),
    // each with a new id. The sound now plays on first SIGHT of a critter: it's allowed only if
    // that critter hasn't been in view (matched on a scan) within the last SOUND_COOLDOWN_MS. This
    // used to be "time since the last SOUND" keyed by the raw resolved name string, which let the
    // catch animation's entity through whenever its name text differed even slightly from the
    // original's (a different key = a fresh cooldown) - the double play on detect + catch. Now:
    //  - the key is the canonical critter (any known critter name found inside the text), so
    //    differently-formatted text for the same critter still collapses to one key;
    //  - "last seen" is refreshed on every scan the critter is matched, so a critter that stays
    //    in view across the whole approach + catch never re-sounds, even past 60s;
    //  - a name that can't be tied to any known critter shares one UNKNOWN key and is also
    //    skipped while any other Sparkling was in view within GRACE_MS (can't tell it apart from
    //    the one already announced).
    // Two DIFFERENT Sparklings still each get their own sound. nearby/names (and the border)
    // are untouched by all this and keep updating live.
    private const val SOUND_COOLDOWN_MS = 60_000L
    private const val GRACE_MS = 10_000L
    private const val UNKNOWN_KEY = "?"
    private val lastSeenMsByKey = mutableMapOf<String, Long>()
    private var lastAnyMatchMs = 0L

    private val LOGGER = org.slf4j.LoggerFactory.getLogger("csafari-sparkling")

    /** The known critter whose name appears inside [text] (longest wins), or null. */
    private fun canonicalName(text: String): String? =
        Critters.ALL.filter { text.contains(it.name, ignoreCase = true) }
            .maxByOrNull { it.name.length }?.name

    /** One scanned entity's result, for the /csafari sparkling debug command - lets you check
     *  what the last scan actually saw and matched without needing to spot the border in game. */
    data class ScanEntry(
        val entityId: Int,
        val rawText: String,
        val critterGuess: String?,
        val matched: Boolean
    )

    @Volatile
    var lastScan: List<ScanEntry> = emptyList()
        private set

    fun tick() {
        if (!enabled || SafariTracker.currentRun == null) {
            nearby = false
            names = emptySet()
            previouslyMatched = emptySet()
            tickCounter = 0
            // No run active: forget what was in view, so the next run's first sight of a critter
            // (even one with the same name as the last run's) sounds normally.
            lastSeenMsByKey.clear()
            lastAnyMatchMs = 0L
            return
        }
        tickCounter++
        if (tickCounter < SCAN_INTERVAL_TICKS) return
        tickCounter = 0

        val level = Minecraft.getInstance().level
        if (level == null) {
            nearby = false
            names = emptySet()
            return
        }

        // Maps entity id -> resolved critter name for everything matched this scan. Built as
        // one map (not two separate sets) specifically so a newly-appearing id can be turned
        // straight back into its name below, without a second lookup pass.
        val matchedIdToName = mutableMapOf<Int, String>()
        val scanLog = mutableListOf<ScanEntry>()

        for (entity in level.entitiesForRendering()) {
            val name = resolveSparklingName(entity, scanLog) ?: continue
            matchedIdToName[entity.id] = name
        }

        nearby = matchedIdToName.isNotEmpty()
        names = matchedIdToName.values.toSet()
        lastScan = scanLog

        // Decide the alert sound for each newly-appearing entity id. A new id is not by itself a
        // new critter (respawn after a failed catch, the critter-in-the-ball during a catch), so
        // whether it sounds depends on whether that CRITTER was already in view - see the comment
        // on SOUND_COOLDOWN_MS. Every decision is logged ("csafari-sparkling") so a stray double
        // play can be traced from latest.log.
        val nowMs = System.currentTimeMillis()
        val hadRecentMatch = nowMs - lastAnyMatchMs <= GRACE_MS // read BEFORE it's refreshed below
        val keys = matchedIdToName.mapValues { (_, name) -> canonicalName(name) ?: UNKNOWN_KEY }

        for ((id, name) in matchedIdToName) {
            if (id in previouslyMatched) continue
            val key = keys.getValue(id)
            val lastSeen = lastSeenMsByKey[key]
            val blockedReason = when {
                lastSeen != null && nowMs - lastSeen < SOUND_COOLDOWN_MS ->
                    "critter already in view ${nowMs - lastSeen}ms ago"
                key == UNKNOWN_KEY && hadRecentMatch ->
                    "unrecognised name while another Sparkling was in view"
                else -> null
            }
            LOGGER.info("[csafari] new Sparkling entity id={} name='{}' key='{}' -> {}", id, name, key, blockedReason ?: "PLAY")
            if (blockedReason == null) ShinySoundConfig.play()
        }

        // Refresh "last seen" for everything matched this scan (after the decisions above).
        for (key in keys.values) lastSeenMsByKey[key] = nowMs
        if (matchedIdToName.isNotEmpty()) lastAnyMatchMs = nowMs
        previouslyMatched = matchedIdToName.keys.toSet()
    }

    /**
     * Returns a display name if [entity] looks like a Sparkling critter, or null. Tries the
     * literal-text match first (cheap, exact), then falls back to particle proximity for
     * critters whose nametag doesn't say "SPARKLING". Every candidate it looks at - matched or
     * not - gets appended to [debug] so /csafari sparkling debug can show the full scan.
     */
    private fun resolveSparklingName(entity: Entity, debug: MutableList<ScanEntry>): String? {
        val rawTexts = listOfNotNull(
            (entity as? Player)?.gameProfile?.name,
            entity.customName?.string
        )

        // Channel 1: literal "SPARKLING" text somewhere in the name.
        for (raw in rawTexts) {
            if (!raw.lowercase().contains(MATCH)) continue
            // Strip the "SPARKLING" marker (any case/position) and resolve what's left to a
            // real critter name; fall back to the cleaned raw text if it doesn't resolve.
            val stripped = Regex("(?i)sparkling").replace(raw, "").trim()
            val resolved = Critters.matchAtStart(stripped)?.name ?: stripped.ifBlank { raw.trim() }
            debug.add(ScanEntry(entity.id, raw, resolved, matched = true))
            return resolved
        }

        // Channel 2: no "SPARKLING" text, but the raw name resolves to a real critter AND a
        // shiny-looking particle was recorded near this entity very recently.
        for (raw in rawTexts) {
            val critter = Critters.matchAtStart(raw.trim()) ?: continue
            val near = ParticleTracker.hasNearby(
                entity.x, entity.y, entity.z,
                PARTICLE_RADIUS, SHINY_PARTICLE_HINTS, PARTICLE_MAX_AGE_MS
            )
            debug.add(ScanEntry(entity.id, raw, critter.name, matched = near))
            if (near) return critter.name
        }

        return null
    }
}
