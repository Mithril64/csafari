package dev.xispana.safari

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket
import org.slf4j.LoggerFactory
import java.util.Optional

/**
 * "Click anywhere to accept Hideyho": while chat is open and a Hideyho "Sure" prompt is pending,
 * a left click ANYWHERE on the screen accepts it, instead of needing to hit the small clickable
 * chat text exactly. Same idea as SkyblockOverhaul's Sphinx solver click handler
 * (SphinxSolver.onGuiMouseClick -> any left click on a ChatScreen), but with nothing to solve:
 * Hideyho's prompt has one deterministic accept option, so this only widens the click target. It
 * still takes one real click from you per prompt - it never fires on its own.
 *
 * WHAT THE PROMPT ACTUALLY IS (confirmed from a real log): the line "Select an option: [Sure]
 * [No thanks...]" has no commands in it. Each button is a ClickEvent.Custom -
 *   [Sure]       -> Custom(id=skyblock:dialogue_response, payload={npcId:"hideyho",responseKey:"r_4_1"})
 *   [No thanks…] -> Custom(id=skyblock:dialogue_response, payload={npcId:"hideyho",responseKey:"r_4_2"})
 * Vanilla handles a Custom click by sending a ServerboundCustomClickActionPacket(id, payload) -
 * that's exactly what accept() sends, using the id and payload captured off the message itself
 * (nothing hardcoded, so the responseKey is whatever that prompt carried). The first version of
 * this only read RunCommand click events, which is why it never captured anything.
 *
 * "Sure" is picked by its visible text (brackets stripped), only if a Hideyho line was seen within
 * RECENT_HIDEYHO_MS (the prompt line itself never says Hideyho) - with a fallback of any Custom
 * click whose payload names hideyho and whose text doesn't start with "No".
 *
 * LOGGING: log only, never chat (an earlier chat echo of a matched line re-triggered its own
 * detector and crashed the client). Search latest.log for "csafari-hideyho".
 *
 * Expires after PENDING_TTL_MS so a stale prompt can't be accepted by an unrelated chat click, and
 * accepts at most one click per CLICK_COOLDOWN_MS.
 */
object HideyhoAutoAccept {

    private val LOGGER = LoggerFactory.getLogger("csafari-hideyho")

    var enabled: Boolean = true

    private const val PENDING_TTL_MS = 30_000L
    private const val RECENT_HIDEYHO_MS = 10_000L

    private val FORMAT_CODE = Regex("§.")

    private data class Pending(val action: ClickEvent.Custom, val atMs: Long)

    @Volatile
    private var pending: Pending? = null
    private var lastHideyhoLineMs = 0L

    /** Called from the GAME and CHAT message listeners for every received line. */
    fun onMessage(message: Component) {
        if (!enabled) return
        val plain = FORMAT_CODE.replace(message.string, "").trim()
        val mentionsHideyho = plain.contains("hideyho", ignoreCase = true)
        if (mentionsHideyho) lastHideyhoLineMs = System.currentTimeMillis()
        val isPromptLine = plain.contains("Select an option", ignoreCase = true)

        // Every text segment with whatever click event it carries (any type).
        val segments = mutableListOf<Pair<String, ClickEvent?>>()
        message.visit(
            { style: Style, text: String ->
                segments.add(text to style.clickEvent)
                Optional.empty<Any>()
            },
            Style.EMPTY
        )

        if (!isPromptLine) return

        LOGGER.info(
            "[csafari] segments: {}",
            segments.joinToString(" | ") { (t, c) ->
                "'$t' -> " + (c?.let { "${it.javaClass.simpleName}($it)" } ?: "no click event")
            }
        )

        val recentHideyho = System.currentTimeMillis() - lastHideyhoLineMs <= RECENT_HIDEYHO_MS

        // Strip the square brackets / trailing spaces and dots: "[Sure] " -> "Sure".
        fun label(t: String) = t.trim().trim('[', ']').trim().trimEnd('.')

        val customs = segments.mapNotNull { (text, click) ->
            if (click is ClickEvent.Custom) label(text) to click else null
        }

        val sure = customs.firstOrNull { (text, _) -> recentHideyho && text.equals("Sure", ignoreCase = true) }
            ?: customs.firstOrNull { (text, click) ->
                click.payload().toString().contains("hideyho", ignoreCase = true) &&
                    !text.startsWith("No", ignoreCase = true)
            }

        if (sure == null) {
            LOGGER.info("[csafari] prompt line seen but no accept option matched (recentHideyho={}): {}", recentHideyho, customs)
            return
        }
        LOGGER.info("[csafari] Hideyho prompt captured: text='{}' id='{}' payload='{}'", sure.first, sure.second.id(), sure.second.payload())
        pending = Pending(sure.second, System.currentTimeMillis())
    }

    /** Registers the click-anywhere handler on every screen; acts only on ChatScreen. */
    fun registerClickHandler() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is ChatScreen) return@register
            ScreenMouseEvents.beforeMouseClick(screen).register { _, click ->
                if (click.buttonInfo().button != 0) return@register
                accept()
            }
        }
    }

    // Double-click protection: at most one accepted click per second, however fast you click.
    // Checked BEFORE the pending prompt is consumed, so a stray second click inside the window
    // can't swallow or re-send anything.
    private const val CLICK_COOLDOWN_MS = 1_000L
    private var lastClickMs = 0L

    private fun accept() {
        if (!enabled) return
        val p = pending ?: return
        val now = System.currentTimeMillis()
        if (now - lastClickMs < CLICK_COOLDOWN_MS) return
        lastClickMs = now
        pending = null // one prompt, one accept
        if (now - p.atMs > PENDING_TTL_MS) {
            LOGGER.info("[csafari] pending Hideyho prompt expired, ignoring click")
            return
        }
        val connection = Minecraft.getInstance().player?.connection ?: return
        LOGGER.info("[csafari] accepting Hideyho prompt: id='{}' payload='{}'", p.action.id(), p.action.payload())
        // Same packet vanilla sends when you click a Custom click event in chat.
        connection.send(ServerboundCustomClickActionPacket(p.action.id(), p.action.payload()))
    }

    /** True while a prompt is waiting for a click - lets the toggle command report status. */
    fun hasPending(): Boolean =
        pending?.let { System.currentTimeMillis() - it.atMs <= PENDING_TTL_MS } ?: false

    fun notice(): Component =
        Component.literal("Hideyho click-to-accept ${if (enabled) "on" else "off"}")
            .withStyle(ChatFormatting.YELLOW)
}
