package dev.xispana.safari

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
// 26.1.2 renamed GuiGraphics -> GuiGraphicsExtractor as part of the rendering-pipeline rewrite.
import net.minecraft.client.gui.GuiGraphicsExtractor

object SafariHud {

    var enabled: Boolean = true
    var x: Int = 4
    var y: Int = 4

    private const val MARGIN = 4

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val AQUA = 0xFF55FFFF.toInt()
    private const val GRAY = 0xFFAAAAAA.toInt()
    private const val GOLD = 0xFFFFAA00.toInt()

    // Per-biome colours.
    private const val CAVERN_ORANGE = 0xFFFF8800.toInt()
    private const val FOREST_GREEN = 0xFF55FF55.toInt()
    private const val HAUNTED_PURPLE = 0xFFAA55FF.toInt()
    private const val ICY_BLUE = 0xFF55AAFF.toInt()

    private fun colorFor(biome: Biome): Int = when (biome) {
        Biome.CAVERN -> CAVERN_ORANGE
        Biome.FOREST -> FOREST_GREEN
        Biome.HAUNTED -> HAUNTED_PURPLE
        Biome.ICY -> ICY_BLUE
    }

    private data class Segment(val text: String, val color: Int)

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") delta: DeltaTracker) {
        if (!enabled) return
        val run = SafariTracker.currentRun ?: return
        val mc = Minecraft.getInstance()
        val font = mc.font

        renderRunPanel(graphics, font, run)
        renderSessionAverage(graphics, font, mc)
    }

    private fun renderRunPanel(graphics: GuiGraphicsExtractor, font: Font, run: SafariRun) {
        val missingByBiome = run.missingByBiome()
        val lines: List<List<Segment>> = buildList {
            add(listOf(Segment("Critter Safari", AQUA)))
            add(listOf(Segment("Time  ${SafariRun.formatTime(run.elapsedMs())}", WHITE)))
            for (stat in run.biomeStats()) {
                val stats = Segment(
                    "${stat.biome.display.padEnd(8)}${stat.found}/${stat.total}",
                    colorFor(stat.biome)
                )
                val missing = missingByBiome[stat.biome]
                if (missing.isNullOrEmpty()) {
                    add(listOf(stats))
                } else {
                    add(listOf(stats, Segment("   " + missing.joinToString(", ") { it.name }, GRAY)))
                }
            }
            if (run.sparkling.isNotEmpty()) {
                add(listOf(Segment("Sparkling ${run.sparkling.size}", GOLD)))
            }
        }

        var cy = y
        for (segments in lines) {
            var cx = x
            for (segment in segments) {
                // GuiGraphicsExtractor.text(font, text, x, y, color, shadow) replaced drawString().
                graphics.text(font, segment.text, cx, cy, segment.color, true)
                cx += font.width(segment.text)
            }
            cy += font.lineHeight + 1
        }
    }

    /**
     * Session average, pinned to the top right. Only rendered while a run is active (same
     * gate as the left panel) - you're already going to be in a run whenever this matters,
     * so there's no need for it to linger on screen between runs.
     *
     * Right-aligned against the window's scaled width (mc.window.guiScaledWidth) rather than
     * a GuiGraphicsExtractor method, since Window's scaled-width getter is basic, long-stable
     * API - lower risk than guessing at a method on the renamed GUI class.
     */
    private fun renderSessionAverage(graphics: GuiGraphicsExtractor, font: Font, mc: Minecraft) {
        val avgMs = SessionStats.averageMs ?: return
        val text = "Session avg: ${SafariRun.formatTime(avgMs)}"
        val screenWidth = mc.window.guiScaledWidth
        val tx = screenWidth - font.width(text) - MARGIN
        graphics.text(font, text, tx, MARGIN, AQUA, true)
    }
}
