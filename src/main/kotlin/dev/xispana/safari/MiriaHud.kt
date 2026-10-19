package dev.xispana.safari

import net.minecraft.client.DeltaTracker
// 26.1.2 renamed GuiGraphics -> GuiGraphicsExtractor as part of the rendering-pipeline rewrite.
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.Minecraft
import java.time.LocalTime

/**
 * Bottom-left countdown to the next Miria contest. The Critter Safari run panel already owns
 * top-left and the session average owns top-right (both in SafariHud), so this sits in the
 * bottom-left corner instead - always visible, not gated to an active safari run, since the
 * contest runs on its own schedule regardless of what you're doing.
 *
 * Back to two lines - instead of a separate third line, the "Miria's Contest" title itself
 * now doubles as the ticket-secured indicator: red while MiriaTicketDetector.ticketSecured is
 * false (the default, until a contest's sidebar tier actually proves otherwise), green once
 * it's confirmed UNCOMMON+. Plain aqua is no longer used.
 */
object MiriaHud {

    var enabled: Boolean = true
    var x: Int = 4
    private const val MARGIN_BOTTOM = 4

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GOLD = 0xFFFFAA00.toInt()
    private const val GREEN = 0xFF55FF55.toInt()
    private const val RED = 0xFFFF5555.toInt()

    // Last few seconds before a mark get a louder color so a glance at the corner is enough
    // to know "it's about to start", not just "some number is counting down".
    private const val IMMINENT_THRESHOLD_SEC = 30L

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") delta: DeltaTracker) {
        if (!enabled || !MiriaConfig.hudEnabled) return
        val mc = Minecraft.getInstance()
        val font = mc.font

        val secondsLeft = ContestSchedule.secondsUntilNext(LocalTime.now())
        val color = if (secondsLeft <= IMMINENT_THRESHOLD_SEC) GOLD else WHITE
        val titleColor = if (MiriaTicketDetector.ticketSecured) GREEN else RED

        val lineHeight = font.lineHeight + 1
        val screenHeight = mc.window.guiScaledHeight
        val y = screenHeight - MARGIN_BOTTOM - lineHeight * 2

        graphics.text(font, "Miria's Contest", x, y, titleColor, true)
        graphics.text(font, "Next in  ${ContestSchedule.formatCountdown(secondsLeft)}", x, y + lineHeight, color, true)
    }
}
