package dev.xispana.safari

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.armadillo.Armadillo

/**
 * Unlike Hideyho (a fake-player disguise matched by name), Armadillo is a real vanilla mob
 * type - so this is a plain type check, not name-based detection.
 *
 * Previously this was deliberately left unscoped (Armadillo isn't one of the 37 Critter
 * Safari critters, so the reasoning was the ESP is useful wherever you run into one). Per your
 * request, that's reversed now: it only highlights while an active safari run is in progress,
 * same gating as HideyhoDetector, so it stays quiet for any Armadillo encountered outside a run.
 *
 * Armadillo's package (`net.minecraft.world.entity.animal.armadillo.Armadillo`) is confirmed
 * against real Mojang-mapped output (mappings.dev, snapshot 25w08a) rather than guessed -
 * same verification standard as the Painting fix, after getting that one wrong twice in a
 * row by guessing blind.
 */
object ArmadilloDetector {

    var enabled: Boolean = true

    fun matches(entity: Entity): Boolean {
        if (!enabled) return false
        if (SafariTracker.currentRun == null) return false // only while a safari run is active
        return entity is Armadillo
    }
}
