package dev.xispana.safari.mixin;

import dev.xispana.safari.ArmadilloDetector;
import dev.xispana.safari.HideyhoDetector;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces the vanilla "glowing" outline (the same effect the Glowing status effect and
 * spectral arrows use, which the renderer draws through blocks via a stencil pass) for any
 * entity {@link HideyhoDetector} or {@link ArmadilloDetector} identifies as a match. Both
 * detectors share this one proven-working injection point rather than each getting their
 * own mixin - this method target has been confirmed working in-game since the Hideyho ESP
 * shipped, so reusing it for Armadillo carries none of the method/package-guessing risk a
 * brand new mixin would.
 *
 * isCurrentlyGlowing() has been the Mojang-mapped name for this check across a very long
 * run of versions - of everything touched by this feature, it's the piece least likely to
 * have moved in 26.1.2's unobfuscation pass, and it's the one piece of this mod actually
 * confirmed working at runtime rather than just compiling.
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void csafari$forceGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (HideyhoDetector.INSTANCE.matches(self) || ArmadilloDetector.INSTANCE.matches(self)) {
            cir.setReturnValue(true);
        }
    }
}
