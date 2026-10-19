package dev.xispana.safari

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
// 26.1 vanilla rename: ResourceLocation -> Identifier.
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.slf4j.LoggerFactory
import java.time.LocalTime

object ContestTracker {

    // Logged to latest.log rather than echoed in chat - an earlier chat-echoed debug line
    // (embedding the matched raw line, which still contained "Torrhus") fed right back into
    // this same detector and looped until the client crashed. A log line can't do that.
    private val LOGGER = LoggerFactory.getLogger("csafari-contest")

    /**
     * True once we've already fired for the current second==0-on-a-mark-minute window, reset
     * back to false as soon as we observe a second that isn't 0. Client ticks run at 20Hz, far
     * finer than the 1-second window this flips on, so under normal play it can't double-fire
     * and can't miss a mark either. A severe lag spike that skips straight over second 0 would
     * miss that one alert - an acceptable, rare edge case, not worth tracking real elapsed time
     * for (the contest is on a real-world schedule regardless of local lag anyway).
     */
    private var armed = true

    // 5 minutes before each contest start (xx:10/30/50, since the contest itself starts at
    // xx:15/35/55) - the "have you secured a ticket yet" warning. Same armed/disarmed pattern
    // as the start-of-contest mark above, just offset by 5 minutes and with its own flag so
    // the two don't interfere with each other.
    private val WARNING_MINUTES: Set<Int> = setOf(10, 30, 50)
    private var warningArmed = true

    fun tick() {
        val now = LocalTime.now()
        val onMark = now.second == 0 && now.minute in ContestSchedule.MARK_MINUTES
        if (onMark) {
            if (armed) {
                armed = false
                onContestStart()
            }
        } else {
            armed = true
        }

        val onWarning = now.second == 0 && now.minute in WARNING_MINUTES
        if (onWarning) {
            if (warningArmed) {
                warningArmed = false
                onFiveMinuteWarning()
            }
        } else {
            warningArmed = true
        }
    }

    private fun onFiveMinuteWarning() {
        if (MiriaTicketDetector.ticketSecured) return // already secured - nothing to warn about
        if (MiriaConfig.soundEnabled) playWarningAlarm()
        if (MiriaConfig.chatEnabled) announceWarning()
        if (MiriaConfig.partyChatEnabled) postToPartyChat("5 minutes left - ticket NOT secured!")
    }

    private fun onContestStart() {
        if (MiriaConfig.soundEnabled) playAlarm()
        if (MiriaConfig.chatEnabled) announce()
        // Always ping at the scheduled start, regardless of ticketSecured - this is a brand
        // new contest window, so by definition nobody's secured a ticket in it yet (the
        // detector itself resets ticketSecured back to false the moment a new window begins).
        if (MiriaConfig.partyChatEnabled) {
            postToPartyChat("Miria's contest is ready!")
            lastPartyPingMs = System.currentTimeMillis()
        }
    }

    // --- Party-chat "ready" ping, re-sent when someone warps in mid-contest ---

    private const val PARTY_PING_COOLDOWN_MS = 60_000L
    private var lastPartyPingMs: Long = 0L

    // Confirmed exact broadcast text (from your own log): "§9§l» §bAcrohh §eis traveling to
    // §aTorrhus Canyon §e§lFOLLOW". That's the real root cause of every earlier "doesn't fire"
    // report - the message carries legacy §-colour codes baked directly into the text itself
    // (e.g. "to §aTorrhus" - the "§a" sits right between the words), and plain \s+ between
    // words never accounts for that. ChatParser.kt already strips these the same way for the
    // safari-catch lines; WARP_TORRHUS now gets the stripped text too, via handleChatLine
    // below, rather than matching against rawLine directly.
    //
    // Matching on the specific phrase "is traveling to Torrhus Canyon" rather than the bare
    // word "Torrhus" - the bare word was too loose and could trip on an ordinary party-chat
    // message that just mentions the place, where this exact phrase is the server's travel
    // broadcast and nothing else would plausibly say it.
    private val WARP_TORRHUS = Regex("(?i)is\\s+traveling\\s+to\\s+Torrhus\\s+Canyon")

    // Same colour-code strip ChatParser uses: a section sign followed by any one character.
    private val FORMAT_CODE = Regex("§.")

    /**
     * Called from the non-cancelling chat listener for every line the client receives. Not
     * tied to any safari-run tracking - this fires any time the travel broadcast shows up,
     * contest active or not, same as the scheduled ping does regardless of what you're doing.
     */
    fun handleChatLine(rawLine: String) {
        val line = FORMAT_CODE.replace(rawLine, "")
        if (!WARP_TORRHUS.containsMatchIn(line)) return
        LOGGER.info("[csafari] matched Torrhus travel line: {}", line)
        if (!MiriaConfig.partyChatEnabled) {
            LOGGER.info("[csafari] party-chat ping is toggled off, not posting")
            return
        }
        if (MiriaTicketDetector.ticketSecured) {
            LOGGER.info("[csafari] ticket already secured, not posting")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastPartyPingMs < PARTY_PING_COOLDOWN_MS) {
            LOGGER.info("[csafari] still on cooldown ({}ms left), not posting", PARTY_PING_COOLDOWN_MS - (now - lastPartyPingMs))
            return
        }
        lastPartyPingMs = now
        // Only the warp-triggered resend gets the "REMINDER!" wording - the scheduled
        // contest-start ping below keeps the original message, per your request.
        postToPartyChat("REMINDER! Miria's contest is ready!")
    }

    private fun postToPartyChat(message: String) {
        if (!PartyTracker.inParty) {
            LOGGER.info("[csafari] not in a party, skipping party-chat post")
            return
        }
        // connection.sendCommand sends exactly what typing "/pc <text>" and hitting enter
        // would - straight to the server, same mechanism SafariTracker's party-chat
        // announcements already use.
        val connection = Minecraft.getInstance().player?.connection
        if (connection == null) {
            LOGGER.info("[csafari] postToPartyChat: no player/connection, cannot send")
            return
        }
        LOGGER.info("[csafari] sending /pc {}", message)
        connection.sendCommand("pc $message")
    }

    /**
     * The bell's "use" (ding) sound, built directly from its resource-location id rather than
     * a `SoundEvents.XXX` constant. The constant name (we first tried `BELL_BLOCK_USE`, then
     * `BLOCK_BELL_USE` - both wrong) is exactly the kind of Java identifier 26.1.2's
     * unobfuscation pass could rename out from under us; the id "block.bell.use" is the actual
     * game/sound-system data the wiki documents and is far more stable than any Java field
     * name pointing at it. `createVariableRangeEvent` is the standard Mojang-mapped factory
     * for building a SoundEvent from an id without needing it to already exist as a constant.
     */
    private val ALARM_SOUND: SoundEvent =
        SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", "block.bell.use"))

    /**
     * Per your request, the 5-minutes-left warning now plays the exact same sound as the
     * xx:15/35/55 contest-start alarm, rather than a distinct one - reusing ALARM_SOUND
     * directly so there's only one sound id to keep in sync.
     */
    private val WARNING_SOUND: SoundEvent = ALARM_SOUND

    /**
     * Plays a local-only alert sound via the UI sound channel - never broadcast to other
     * players, and not tied to the player's in-world position.
     *
     * Both this and playWarningAlarm below used to hardcode `forUI(event, 1.0f)` - that second
     * argument is actually PITCH, not volume (confirmed against mappings.dev: that overload's
     * alias is `master(SoundEvent sound, float pitch)`), so there was never really a volume
     * control here at all until now. Now goes through BoostedSound (see BoostedSound.kt) rather
     * than calling forUI directly - a single instance's gain turned out to be hard-capped at 1.0
     * by the sound engine itself, so values above 100 on /miria volume were being silently
     * capped right back down and never got any louder. BoostedSound stacks full-volume copies
     * instead, which actually sums to something louder. Uses MiriaConfig.bellVolumePercent -
     * kept separate from ShinySoundConfig.volumePercent since these are a different sound you
     * may want at a different level (/miria volume or /csafari miria volume).
     */
    fun playAlarm() {
        BoostedSound.play(ALARM_SOUND, MiriaConfig.bellVolumePercent)
    }

    fun playWarningAlarm() {
        BoostedSound.play(WARNING_SOUND, MiriaConfig.bellVolumePercent)
    }

    private fun announce() {
        val mc = Minecraft.getInstance()
        mc.player?.sendSystemMessage(
            Component.literal("Miria's contest is starting now!").withStyle(ChatFormatting.LIGHT_PURPLE)
        )
    }

    private fun announceWarning() {
        val mc = Minecraft.getInstance()
        mc.player?.sendSystemMessage(
            Component.literal("5 minutes left in Miria's contest - ticket not secured!")
                .withStyle(ChatFormatting.RED)
        )
    }
}
