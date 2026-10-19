package dev.xispana.safari

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * Hideyho's Hypixel wiki entry lists its Mob Type as "Humanoid" - Hypixel's Haunted-biome
 * critters that hide as something else (Hideonwall, Hideonfloor, Hideyho) are typically
 * implemented as fake-player disguises rather than a custom mob model. So this matches on
 * the disguise's GameProfile name rather than its skin texture: the name is plain text sent
 * with the entity and doesn't require decoding or hardcoding any texture data, and it stays
 * correct even if Hypixel changes Hideyho's actual skin later.
 *
 * If Hideyho's disguise turns out to use a different fake username than expected, change
 * MATCH_NAME below - everything else (the mixin, the gating) stays the same.
 */
object HideyhoDetector {

    var enabled: Boolean = true

    private const val MATCH_NAME = "hideyho"

    fun matches(entity: Entity): Boolean {
        if (!enabled) return false
        if (SafariTracker.currentRun == null) return false // only while a safari run is active
        if (entity !is Player) return false
        return entity.gameProfile.name.lowercase().contains(MATCH_NAME)
    }
}
