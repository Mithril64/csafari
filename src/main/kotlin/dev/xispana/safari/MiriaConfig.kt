package dev.xispana.safari

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

private data class MiriaState(
    val hudEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val chatEnabled: Boolean = true,
    val partyChatEnabled: Boolean = true,
    val bellVolumePercent: Int = 100
)

/** Persists the HUD/sound/chat toggles to disk so they survive a game restart. */
object MiriaConfig {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("critter-safari-miria.json")

    var hudEnabled: Boolean = true
        set(value) {
            field = value
            save()
        }

    var soundEnabled: Boolean = true
        set(value) {
            field = value
            save()
        }

    var chatEnabled: Boolean = true
        set(value) {
            field = value
            save()
        }

    /** Posts "Miria's contest is ready!" to party chat on contest start, and again whenever
     *  someone warps to Torrhus mid-contest (see ContestTracker). */
    var partyChatEnabled: Boolean = true
        set(value) {
            field = value
            save()
        }

    /** Separate from ShinySoundConfig.volumePercent on purpose - the two bell alerts
     *  (contest-start and 5-minutes-left) are a completely different sound from the shiny-found
     *  jingle, so you can turn one down without touching the other. Capped at 1000 (10x), not
     *  100 - this is meant to allow boosting past "normal" (100 = 1.0, the sound's own normal
     *  loudness), the cap is just a guard against a typo setting it absurdly high.
     *
     *  Values above 100 only actually get audibly louder because of BoostedSound (see
     *  BoostedSound.kt and ContestTracker.playAlarm/playWarningAlarm) - a plain forUI(event,
     *  pitch, volume) call silently gets clamped back to 1.0 gain by Minecraft's own sound
     *  engine no matter how high volume is set, so before BoostedSound existed, anything above
     *  100 here had no audible effect at all. */
    var bellVolumePercent: Int = 100
        set(value) {
            field = value.coerceIn(0, 1000)
            save()
        }

    init {
        load()
    }

    private fun load() {
        if (!Files.exists(file)) return
        runCatching {
            val text = Files.readString(file)
            val state = gson.fromJson(text, MiriaState::class.java) ?: return
            hudEnabled = state.hudEnabled
            soundEnabled = state.soundEnabled
            chatEnabled = state.chatEnabled
            partyChatEnabled = state.partyChatEnabled
            bellVolumePercent = state.bellVolumePercent
        }
    }

    fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(
                file,
                gson.toJson(MiriaState(hudEnabled, soundEnabled, chatEnabled, partyChatEnabled, bellVolumePercent))
            )
        }
    }
}
