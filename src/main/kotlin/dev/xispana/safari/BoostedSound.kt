package dev.xispana.safari

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent

/**
 * Plays a UI sound with an ACTUAL audible boost past 100%, used by both ShinySoundConfig and
 * ContestTracker's bell alerts.
 *
 * Root cause of "the volume slider doesn't seem to do anything past 100": confirmed via
 * mappings.dev that `SoundEngine` has `VOLUME_MIN`/`VOLUME_MAX` fields - the engine clamps a
 * single sound instance's gain to the [0.0, 1.0] range before it's ever sent to OpenAL. Calling
 * `forUI(event, pitch, 2.5f)` does NOT make that one sound 2.5x louder; the engine silently caps
 * it straight back down to 1.0 (100%), so every value above 100 sounded identical to 100. That's
 * not a bug in the percent-to-float conversion (which was already correct) - it's a hard ceiling
 * built into the engine on any single sound instance, and nothing a mod can configure around on
 * that one instance.
 *
 * The actual fix: when asked for more than 100%, this plays several full-volume (gain 1.0)
 * copies of the sound at once instead of one instance with an inflated gain. Multiple
 * simultaneous full-volume copies of the same sound sum together acoustically - two copies
 * together are audibly louder than one, the same way two speakers playing the same track at once
 * are louder than either alone - and that summing happens in the mixer, after each individual
 * instance's own 1.0 cap, so it isn't subject to that per-instance ceiling. 250% plays two
 * full-volume copies plus one quieter (0.5 gain) copy; 100% or less just plays the single
 * ordinary copy, identical to before this existed.
 */
object BoostedSound {

    fun play(event: SoundEvent, percent: Int) {
        if (percent <= 0) return
        val gain = percent / 100f
        val soundManager = Minecraft.getInstance().soundManager

        if (gain <= 1f) {
            soundManager.play(SimpleSoundInstance.forUI(event, 1.0f, gain))
            return
        }

        val fullCopies = gain.toInt()
        val remainder = gain - fullCopies
        repeat(fullCopies) {
            soundManager.play(SimpleSoundInstance.forUI(event, 1.0f, 1.0f))
        }
        if (remainder > 0.01f) {
            soundManager.play(SimpleSoundInstance.forUI(event, 1.0f, remainder))
        }
    }
}
