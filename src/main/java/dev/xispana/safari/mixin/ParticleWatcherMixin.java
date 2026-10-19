package dev.xispana.safari.mixin;

import dev.xispana.safari.ParticleTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records every particle the client spawns - type and position - into ParticleTracker, which
 * backs both the "shiny particle nearby" fallback in SparklingHighlighter and the `/csafari
 * particles` debug command.
 *
 * Targets the public `addParticle(ParticleOptions, double, double, double, double, double,
 * double)` overload specifically, using its full method descriptor
 * (`addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V`) - ClientLevel has
 * SIX overloads named `addParticle` (confirmed via mappings.dev), several private and with
 * different signatures, so Mixin needs the exact descriptor to disambiguate; a bare method
 * name here would either fail outright or silently match the wrong one.
 *
 * This is the main public entry point the client uses for every particle it renders,
 * confirmed against real Mojang-mapped output (mappings.dev, 1.21.9) rather than guessed -
 * but still one version short of 26.1.2 itself, same caveat as the other mixins in this mod.
 * It lives in the optional mixin config, so if this target doesn't exist on your build, only
 * this feature silently no-ops rather than crashing anything.
 */
@Mixin(ClientLevel.class)
public abstract class ParticleWatcherMixin {

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD")
    )
    private void csafari$recordParticle(
            ParticleOptions particle, double x, double y, double z,
            double xd, double yd, double zd, CallbackInfo ci
    ) {
        ParticleTracker.record(particle, x, y, z);
    }
}
