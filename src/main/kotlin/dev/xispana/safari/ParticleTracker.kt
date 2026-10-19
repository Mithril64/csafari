package dev.xispana.safari

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.registries.BuiltInRegistries
import kotlin.jvm.JvmStatic

/**
 * Records every particle the client spawns (via ParticleWatcherMixin, hooked into
 * `ClientLevel.addParticle`) in a short rolling buffer: type id, position, and timestamp.
 *
 * This exists because "SPARKLING" doesn't always appear in a critter's nametag (per your
 * report) - the in-game sparkle visual (the cross/plus particle glints in your screenshot) is
 * the more reliable signal for those. But which vanilla particle type Hypixel actually reuses
 * for that effect is something I can't know without being in-game myself, and this mod has
 * already guessed wrong twice today on things far more checkable (a Java field name, a class
 * package) than "which of ~80 vanilla particle types a third-party server's resource pack
 * repurposes for a custom visual." So rather than hardcode one guess with real confidence,
 * this buffer also backs `/csafari particles`, which prints what's actually been seen nearby
 * in the last few seconds - point your camera at a Sparkling critter, run it, and tell me the
 * exact id if the default guess list in SparklingHighlighter.SHINY_PARTICLE_HINTS is wrong.
 */
object ParticleTracker {

    data class Sighting(val typeId: String, val x: Double, val y: Double, val z: Double, val atMs: Long)

    private const val MAX_AGE_MS = 4000L
    private const val MAX_ENTRIES = 512 // hard cap so a particle storm can't grow this unbounded

    private val buffer = ArrayDeque<Sighting>()

    // @JvmStatic matters here specifically for `record`: it's called from Java
    // (ParticleWatcherMixin), and a Kotlin `object`'s members are instance methods on the
    // singleton (`ParticleTracker.INSTANCE.record(...)`) from Java's point of view unless
    // marked @JvmStatic - without it, `ParticleTracker.record(...)` from Java doesn't compile
    // ("non-static method ... cannot be referenced from a static context"). Added to the rest
    // of the public API too, since any of these could end up called from a mixin later.
    @JvmStatic
    @Synchronized
    fun record(particle: ParticleOptions, x: Double, y: Double, z: Double) {
        val id = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.type)?.toString() ?: return
        buffer.addLast(Sighting(id, x, y, z, System.currentTimeMillis()))
        while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        pruneOld()
    }

    private fun pruneOld() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        while (buffer.isNotEmpty() && buffer.first().atMs < cutoff) buffer.removeFirst()
    }

    /** All sightings within [maxAgeMs] of now, newest-insertion-order preserved. */
    @JvmStatic
    @Synchronized
    fun recent(maxAgeMs: Long = MAX_AGE_MS): List<Sighting> {
        pruneOld()
        val cutoff = System.currentTimeMillis() - maxAgeMs
        return buffer.filter { it.atMs >= cutoff }
    }

    /** True if any sighting of a type in [typeHints] (substring match) landed within [radius] blocks of (x,y,z) in the last [maxAgeMs]. */
    @JvmStatic
    fun hasNearby(x: Double, y: Double, z: Double, radius: Double, typeHints: Set<String>, maxAgeMs: Long = 1500L): Boolean {
        val r2 = radius * radius
        return recent(maxAgeMs).any { s ->
            typeHints.any { hint -> s.typeId.contains(hint) } &&
                (s.x - x) * (s.x - x) + (s.y - y) * (s.y - y) + (s.z - z) * (s.z - z) <= r2
        }
    }

    /** For `/csafari particles`: type id -> how many times seen in the last [maxAgeMs]. */
    @JvmStatic
    fun frequency(maxAgeMs: Long = MAX_AGE_MS): Map<String, Int> =
        recent(maxAgeMs).groupingBy { it.typeId }.eachCount()
}
