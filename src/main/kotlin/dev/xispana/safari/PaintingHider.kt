package dev.xispana.safari

/**
 * Read by PaintingRenderMixin every frame a Painting entity is about to be drawn. On by
 * default per your request - toggle with `/csafari paintings` if you want them back.
 */
object PaintingHider {
    var enabled: Boolean = true
}
