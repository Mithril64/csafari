package dev.xispana.safari

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
// 26.1 vanilla rename: ResourceLocation -> Identifier.
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import java.nio.file.Files

/** The two shiny-found jingles to choose from, each backed by its own sound event/file under
 *  assets/csafari/sounds. "Pokemon" is the original (`shiny.ogg`); "Wynncraft" is the one you
 *  sent (Wynntils OST - "Infinite Pleasure (Mythic Found)", converted to .ogg). */
enum class ShinySound(val displayName: String, soundPath: String) {
    POKEMON("Pokemon", "shiny"),
    WYNNCRAFT("Wynncraft", "shiny_wynncraft");

    val event: SoundEvent = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("csafari", soundPath))

    companion object {
        fun fromName(name: String): ShinySound? = entries.firstOrNull { it.name == name }
    }
}

private data class ShinySoundState(val selected: String = ShinySound.POKEMON.name, val volumePercent: Int = 100)

/** Persists which shiny-found sound is selected (and how loud it plays), the same way
 *  MiriaConfig persists its toggles. */
object ShinySoundConfig {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("critter-safari-shiny-sound.json")

    var selected: ShinySound = ShinySound.POKEMON
        set(value) {
            field = value
            save()
        }

    /** 0-100, stored as a whole percentage (easier to type as a command argument than a
     *  0.0-1.0 float) and converted down to SimpleSoundInstance's 0.0-1.0 range in play().
     *
     *  This used to be wired to the WRONG parameter: `SimpleSoundInstance.forUI(SoundEvent,
     *  float)`'s second argument is PITCH, not volume - confirmed against mappings.dev, which
     *  names that overload's alias `master(SoundEvent sound, float pitch)`. So every volume
     *  change was actually silently changing playback speed/tone instead of loudness, which is
     *  almost certainly what you were noticing as "goes above 100" acting strangely. Fixed by
     *  switching to the 3-argument overload, `forUI(SoundEvent, pitch, volume)` - pitch is now
     *  always passed as 1.0 (normal) and this value only ever reaches the real volume slot.
     *
     *  What 100 means: 1.0f, the sound's own normal/authored loudness - exactly what this mod
     *  always hardcoded before this setting existed, so 100 is a no-op baseline, not a jump in
     *  volume. It's a multiplier Minecraft applies on top of your in-game Master/volume sliders
     *  (Options > Music & Sounds), not a Windows/OS-level volume - this can't make the game
     *  louder than your system volume allows, only louder or quieter relative to this one sound
     *  event's own normal level. Capped at 1000 (10x), not 100 - per your request this is meant
     *  to allow boosting past "normal", the cap is just a guard against a typo setting it to
     *  something absurd.
     *
     *  Routed through BoostedSound (see BoostedSound.kt) the same as the Miria bell - a plain
     *  forUI(event, pitch, volume) call has its gain hard-clamped to 1.0 by Minecraft's sound
     *  engine itself no matter what's passed in here, so anything above 100 had no audible
     *  effect without this. This briefly got reverted back to the plain forUI call (thinking
     *  this meter "already worked fine" at the time), which is exactly what broke the >100
     *  boost again - re-applying BoostedSound for good. */
    var volumePercent: Int = 100
        set(value) {
            field = value.coerceIn(0, 1000)
            save()
        }

    init {
        load()
    }

    /** Cycles to the next option and returns it, for a single toggle command. */
    fun cycle(): ShinySound {
        val next = ShinySound.entries[(selected.ordinal + 1) % ShinySound.entries.size]
        selected = next
        return next
    }

    fun play() {
        BoostedSound.play(selected.event, volumePercent)
    }

    private fun load() {
        if (!Files.exists(file)) return
        runCatching {
            val text = Files.readString(file)
            val state = gson.fromJson(text, ShinySoundState::class.java) ?: return
            selected = ShinySound.fromName(state.selected) ?: ShinySound.POKEMON
            volumePercent = state.volumePercent
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(ShinySoundState(selected.name, volumePercent)))
        }
    }
}
