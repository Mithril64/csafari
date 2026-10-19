package dev.xispana.safari.mixin;

import dev.xispana.safari.PaintingHider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
// Painting moved from world.entity.decoration into its own world.entity.decoration.painting
// subpackage sometime after 1.21.6 (confirmed via mappings.dev) - a plain package move, not
// a rename, so nothing else about this file is affected by it.
import net.minecraft.world.entity.decoration.painting.Painting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides vanilla Painting entities by targeting the render-visibility GATE rather than the
 * per-entity-type renderer itself.
 *
 * The first version of this mixin targeted `PaintingRenderer.render(Painting, ...)`, which
 * was itself based on a pre-1.20.5 signature. Since the 1.20.5+ "render state" refactor,
 * PaintingRenderer.render() no longer even takes the Painting entity as a parameter (it takes
 * an extracted PaintingRenderState instead, populated by a separate extractRenderState()
 * call) - the old target silently failed to match (likely compounded by a synthetic bridge
 * method Java generates for the generic override), and with this mixin config marked
 * `"required": false`, that failure was silent rather than a crash: the feature just didn't
 * do anything.
 *
 * `EntityRenderDispatcher.shouldRender(Entity, Frustum, double, double, double)` sidesteps
 * all of that churn - it's the dispatcher-level gate that decides whether ANY entity even
 * reaches its type-specific renderer, declared directly (not overriding a generic supertype
 * method), so there's no bridge-method ambiguity, and it hasn't been touched by the
 * render-state refactor since it has nothing to do with how a renderer draws. Verified
 * against real Mojang-mapped output (mappings.dev, snapshot 25w08a) rather than guessed, so
 * this is on much firmer ground than the previous attempt - but 26.1.2 is still a step beyond
 * what was checked, so if this mixin still doesn't apply, that's the next thing to verify.
 *
 * Painting itself also moved packages along the way, from `world.entity.decoration.Painting`
 * to `world.entity.decoration.painting.Painting` sometime after 1.21.6 - also confirmed
 * against mappings.dev rather than guessed. That's a plain package reorganization, not a
 * rename, so it only affects the import here, nothing about the mixin's logic.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class PaintingRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void csafari$hidePainting(
            E entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (PaintingHider.INSTANCE.getEnabled() && entity instanceof Painting) {
            cir.setReturnValue(false);
        }
    }
}
