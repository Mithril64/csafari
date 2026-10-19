package dev.xispana.safari

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
// 26.1.2 renamed GuiGraphics -> GuiGraphicsExtractor as part of the rendering-pipeline rewrite.
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color

/**
 * Draws a pulsing rainbow border around the screen edge while SparklingHighlighter.nearby is
 * true - loud and visible out of the corner of your eye, which is the point: a chat line is
 * easy to miss mid-run, a full-screen border isn't. Drawn as many thin segments around the
 * perimeter instead of one solid color, each segment's hue offset by its position along the
 * edge plus the current time, so the whole ring visibly cycles rather than just flashing a
 * single color - a closer match to "rainbow" than a uniform tinted border would be.
 *
 * Uses `graphics.fill(x1, y1, x2, y2, color)` - the long-stable vanilla GuiGraphics name for
 * a plain filled rectangle, going back years before 26.1.2. The one confirmed rename so far
 * (drawString -> text) was specific to text drawing; nothing points to `fill` itself having
 * moved. Still unverified against this exact build, same caveat as everything else touching
 * GuiGraphicsExtractor - if `fill` doesn't resolve, that's the only thing to check here.
 *
 * Also draws the name(s) of the matched Sparkling critter(s) (SparklingHighlighter.names)
 * centered just under the top edge - the companion piece to the border itself, so a glance
 * tells you not just "something's sparkling" but which critter it is, worth going to catch.
 * Uses `graphics.text(font, text, x, y, color, shadow)` rather than a `drawCenteredString`
 * guess - that's the exact method this project already confirmed works on this build
 * (SafariHud, MiriaHud both use it), so centering is done by hand via `font.width(text)`
 * instead of trusting an unverified second text-drawing method to also exist.
 */
object SparklingBorderOverlay {

    private const val THICKNESS = 4
    private const val SEGMENTS_PER_EDGE = 40
    private const val CYCLE_MS = 2000L // one full hue rotation every 2 seconds

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") delta: DeltaTracker) {
        // SparklingHighlighter.nearby is already gated on its own `enabled` flag (and on
        // whether a run is active), so there's nothing extra to check here - one toggle,
        // not two that could drift out of sync.
        if (!SparklingHighlighter.nearby) return
        val mc = Minecraft.getInstance()
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        val timeOffset = (System.currentTimeMillis() % CYCLE_MS).toFloat() / CYCLE_MS

        drawEdge(graphics, 0, 0, w, THICKNESS, timeOffset, horizontal = true)           // top
        drawEdge(graphics, 0, h - THICKNESS, w, THICKNESS, timeOffset, horizontal = true) // bottom
        drawEdge(graphics, 0, 0, THICKNESS, h, timeOffset, horizontal = false)          // left
        drawEdge(graphics, w - THICKNESS, 0, THICKNESS, h, timeOffset, horizontal = false) // right

        val label = SparklingHighlighter.names.sorted().joinToString(", ")
        if (label.isNotEmpty()) {
            val text = "Sparkling: $label"
            val font = mc.font
            val textX = (w - font.width(text)) / 2
            graphics.text(font, text, textX, THICKNESS + 4, 0xFFFF55, true)
        }
    }

    private fun drawEdge(
        graphics: GuiGraphicsExtractor,
        startX: Int, startY: Int, totalW: Int, totalH: Int,
        timeOffset: Float, horizontal: Boolean
    ) {
        val length = if (horizontal) totalW else totalH
        for (i in 0 until SEGMENTS_PER_EDGE) {
            val frac = i.toFloat() / SEGMENTS_PER_EDGE
            val hue = (frac + timeOffset) % 1.0f
            val color = 0xFF000000.toInt() or (Color.HSBtoRGB(hue, 1f, 1f) and 0xFFFFFF)

            val segLen = length / SEGMENTS_PER_EDGE
            val segStart = i * segLen
            val segEnd = if (i == SEGMENTS_PER_EDGE - 1) length else segStart + segLen

            if (horizontal) {
                graphics.fill(startX + segStart, startY, startX + segEnd, startY + totalH, color)
            } else {
                graphics.fill(startX, startY + segStart, startX + totalW, startY + segEnd, color)
            }
        }
    }
}
