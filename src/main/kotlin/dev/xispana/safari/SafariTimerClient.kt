package dev.xispana.safari

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.CommandNode
import net.fabricmc.api.ClientModInitializer
// 26.1 Fabric API rename: ClientCommandManager -> ClientCommands.
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
// 26.1 vanilla rename: ResourceLocation -> Identifier.
import net.minecraft.resources.Identifier

object SafariTimerClient : ClientModInitializer {

    const val MOD_ID = "critter-safari-timer"

    override fun onInitializeClient() {
        // ALLOW_GAME is cancellable: returning false hides the vanilla line. GAME (the
        // non-cancellable variant) can only observe messages, not suppress them.
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            SafariTracker.handleChatLine(message.string)
        }

        // Only observing for the Torrhus travel broadcast here, never hiding anything - GAME
        // rather than ALLOW_GAME, so it can't interfere with whatever else (vanilla or another
        // mod) also wants to see this line.
        // PartyTracker runs first on purpose: the Torrhus line itself is also the evidence
        // PartyTracker uses to confirm party membership, so it needs to update `inParty`
        // before ContestTracker checks it on that very same line - otherwise the first-ever
        // travel broadcast in a session would still get skipped as "not in a party".
        ClientReceiveMessageEvents.GAME.register { message, _ ->
            PartyTracker.handleChatLine(message.string)
            ContestTracker.handleChatLine(message.string)
            // Needs the full Component (not just .string) to read the clickable segments.
            HideyhoAutoAccept.onMessage(message)
        }

        // Click anywhere on an open chat screen to accept a pending Hideyho prompt.
        HideyhoAutoAccept.registerClickHandler()
        // Also listening on CHAT (player-sent-shaped messages) - the GAME-only hook wasn't
        // catching the travel broadcast, which suggests Hypixel may be sending that
        // particular line through the chat pipeline rather than the game/system one. Cheap
        // to listen on both: handleChatLine's own regex, toggle, cooldown and ticket-secured
        // gates make a duplicate delivery harmless.
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            PartyTracker.handleChatLine(message.string)
            ContestTracker.handleChatLine(message.string)
            // Same story as the Torrhus broadcast: the "[MOB] Hideyho: ..." / "Select an option"
            // lines never showed up on GAME alone (no log output at all), so they're likely
            // delivered through this pipeline. A line arriving on both is harmless - it just
            // overwrites the same pending prompt.
            HideyhoAutoAccept.onMessage(message)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            SafariTracker.tick(client)
            // Runs on its own real-world schedule, independent of any safari run.
            ContestTracker.tick()
            SparklingHighlighter.tick()
            MiriaTicketDetector.tick(client)
        }

        // Leaving the server ends any run in progress.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SafariTracker.end(announce = false)
        }

        // HudRenderCallback was removed; HudElementRegistry is the replacement.
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath(MOD_ID, "safari-overlay")
        ) { graphics, tickCounter -> SafariHud.render(graphics, tickCounter) }

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath(MOD_ID, "miria-overlay")
        ) { graphics, tickCounter -> MiriaHud.render(graphics, tickCounter) }

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath(MOD_ID, "sparkling-border")
        ) { graphics, tickCounter -> SparklingBorderOverlay.render(graphics, tickCounter) }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            // Renamed from /safari to /csafari (plus a /cs shorthand) because /safari
            // collided with another mod's command. /cs is registered as a plain alias via
            // Brigadier's own `.redirect(...)` - not a mapped Minecraft API, just core
            // com.mojang.brigadier behavior, so this carries none of the usual
            // version-guessing risk the rest of this mod has to worry about.
            // Registered /miria first so its node exists to redirect() into from under
            // /csafari below - "/miria volume ..." and "/csafari miria volume ..." both
            // now work and share the exact same command tree (redirect, not a copy).
            // This fixes a real bug: /csafari miria volume was never actually registered
            // anywhere before now, only the bare top-level /miria was - any "/csafari miria
            // ..." typed previously was simply an unrecognized command the whole way through.
            val miriaNode = dispatcher.register(buildMiriaCommand())
            val csafariNode = dispatcher.register(buildCommand(miriaNode))
            dispatcher.register(ClientCommands.literal("cs").redirect(csafariNode))
        }
    }

    private fun buildCommand(
        miriaNode: CommandNode<FabricClientCommandSource>
    ): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("csafari")
            .executes { ctx ->
                val run = SafariTracker.currentRun ?: SafariTracker.lastRun
                if (run == null) {
                    ctx.source.sendFeedback(
                        Component.literal("No safari run tracked yet.").withStyle(ChatFormatting.GRAY)
                    )
                } else {
                    val title = if (SafariTracker.currentRun != null) "Current run" else "Last run"
                    SafariTracker.printSummary(run, title)
                }
                1
            }
            .then(
                ClientCommands.literal("missing").executes { ctx ->
                    val run = SafariTracker.currentRun ?: SafariTracker.lastRun
                    if (run == null) {
                        ctx.source.sendFeedback(
                            Component.literal("No safari run tracked yet.").withStyle(ChatFormatting.GRAY)
                        )
                    } else {
                        SafariTracker.printMissing(run)
                    }
                    1
                }
            )
            .then(
                ClientCommands.literal("pb").executes {
                    SafariTracker.printBests(); 1
                }
            )
            .then(
                ClientCommands.literal("session")
                    .executes {
                        SafariTracker.printSessionStats(); 1
                    }
                    .then(
                        ClientCommands.literal("reset").executes { ctx ->
                            SafariTracker.resetSession()
                            ctx.source.sendFeedback(
                                Component.literal("Session stats reset (runs, average, PB, shiny log).")
                                    .withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                    )
            )
            .then(
                ClientCommands.literal("start").executes {
                    SafariTracker.startManual(); 1
                }
            )
            .then(
                ClientCommands.literal("stop").executes {
                    SafariTracker.stopManual(); 1
                }
            )
            .then(
                ClientCommands.literal("reset").executes {
                    SafariTracker.reset(); 1
                }
            )
            .then(
                ClientCommands.literal("hud").executes { ctx ->
                    SafariHud.enabled = !SafariHud.enabled
                    ctx.source.sendFeedback(
                        Component.literal("HUD ${if (SafariHud.enabled) "on" else "off"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("esp").executes { ctx ->
                    HideyhoDetector.enabled = !HideyhoDetector.enabled
                    ctx.source.sendFeedback(
                        Component.literal("Hideyho ESP ${if (HideyhoDetector.enabled) "on" else "off"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("hideyhoaccept").executes { ctx ->
                    HideyhoAutoAccept.enabled = !HideyhoAutoAccept.enabled
                    ctx.source.sendFeedback(HideyhoAutoAccept.notice())
                    1
                }
            )
            .then(
                ClientCommands.literal("paintings").executes { ctx ->
                    PaintingHider.enabled = !PaintingHider.enabled
                    ctx.source.sendFeedback(
                        Component.literal("Paintings ${if (PaintingHider.enabled) "hidden" else "visible"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("armadillo").executes { ctx ->
                    ArmadilloDetector.enabled = !ArmadilloDetector.enabled
                    ctx.source.sendFeedback(
                        Component.literal("Armadillo ESP ${if (ArmadilloDetector.enabled) "on" else "off"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("sparkling")
                    .executes { ctx ->
                        SparklingHighlighter.enabled = !SparklingHighlighter.enabled
                        ctx.source.sendFeedback(
                            Component.literal("Sparkling border ${if (SparklingHighlighter.enabled) "on" else "off"}")
                                .withStyle(ChatFormatting.YELLOW)
                        )
                        1
                    }
                    .then(
                        ClientCommands.literal("debug").executes { ctx ->
                            val scan = SparklingHighlighter.lastScan
                            if (scan.isEmpty()) {
                                ctx.source.sendFeedback(
                                    Component.literal("No Sparkling candidates in the last scan.")
                                        .withStyle(ChatFormatting.GRAY)
                                )
                            } else {
                                ctx.source.sendFeedback(
                                    Component.literal("Last scan (${scan.size} candidate(s)):")
                                        .withStyle(ChatFormatting.YELLOW)
                                )
                                scan.forEach { entry ->
                                    val status = if (entry.matched) "MATCHED" else "no particle"
                                    val color = if (entry.matched) ChatFormatting.GREEN else ChatFormatting.GRAY
                                    ctx.source.sendFeedback(
                                        Component.literal(
                                            "  [${entry.entityId}] \"${entry.rawText}\" -> ${entry.critterGuess ?: "?"} ($status)"
                                        ).withStyle(color)
                                    )
                                }
                            }
                            1
                        }
                    )
            )
            .then(
                ClientCommands.literal("shinysound")
                    .executes { ctx ->
                        val next = ShinySoundConfig.cycle()
                        ctx.source.sendFeedback(
                            Component.literal("Shiny sound: ${next.displayName}").withStyle(ChatFormatting.YELLOW)
                        )
                        1
                    }
                    .then(
                        ClientCommands.literal("pokemon").executes { ctx ->
                            ShinySoundConfig.selected = ShinySound.POKEMON
                            ctx.source.sendFeedback(
                                Component.literal("Shiny sound: Pokemon").withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                    )
                    .then(
                        ClientCommands.literal("wynncraft").executes { ctx ->
                            ShinySoundConfig.selected = ShinySound.WYNNCRAFT
                            ctx.source.sendFeedback(
                                Component.literal("Shiny sound: Wynncraft").withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                    )
                    .then(
                        ClientCommands.literal("test").executes { ctx ->
                            ShinySoundConfig.play()
                            ctx.source.sendFeedback(
                                Component.literal(
                                    "Played shiny sound: ${ShinySoundConfig.selected.displayName} at ${ShinySoundConfig.volumePercent}%"
                                ).withStyle(ChatFormatting.GRAY)
                            )
                            1
                        }
                    )
                    .then(
                        ClientCommands.literal("volume").executes { ctx ->
                            ctx.source.sendFeedback(
                                Component.literal("Shiny sound volume: ${ShinySoundConfig.volumePercent}%")
                                    .withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }.then(
                            // 0-1000, not 0-100 - lets this go above "normal" (100) as a real
                            // boost, per your request. The old 100-cap here was rejecting
                            // anything over 100 as invalid Brigadier syntax entirely, which is
                            // why it looked like "not a valid command" instead of just clamping.
                            ClientCommands.argument("percent", IntegerArgumentType.integer(0, 1000))
                                .executes { ctx ->
                                    val percent = IntegerArgumentType.getInteger(ctx, "percent")
                                    ShinySoundConfig.volumePercent = percent
                                    ctx.source.sendFeedback(
                                        Component.literal("Shiny sound volume: ${ShinySoundConfig.volumePercent}%")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                        )
                    )
            )
            .then(
                ClientCommands.literal("particles").executes { ctx ->
                    val freq = ParticleTracker.frequency()
                    if (freq.isEmpty()) {
                        ctx.source.sendFeedback(
                            Component.literal("No particles recorded in the last few seconds.")
                                .withStyle(ChatFormatting.GRAY)
                        )
                    } else {
                        ctx.source.sendFeedback(
                            Component.literal("Particles seen nearby (last 4s):").withStyle(ChatFormatting.YELLOW)
                        )
                        freq.entries.sortedByDescending { it.value }.forEach { (id, count) ->
                            ctx.source.sendFeedback(
                                Component.literal("  $id: $count").withStyle(ChatFormatting.GRAY)
                            )
                        }
                    }
                    1
                }
            )
            .then(
                ClientCommands.literal("announce")
                    .executes { ctx ->
                        AnnounceConfig.enabled = !AnnounceConfig.enabled
                        ctx.source.sendFeedback(
                            Component.literal("Auto party-chat announcements ${if (AnnounceConfig.enabled) "on" else "off"}.")
                                .withStyle(ChatFormatting.YELLOW)
                        )
                        1
                    }
                    .then(
                        ClientCommands.literal("add")
                            .then(
                                ClientCommands.argument("seconds", IntegerArgumentType.integer(1))
                                    .executes { ctx ->
                                        val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
                                        AnnounceConfig.addMark(seconds)
                                        ctx.source.sendFeedback(
                                            Component.literal("Will post missing list to /pc at ${seconds}s.")
                                                .withStyle(ChatFormatting.YELLOW)
                                        )
                                        1
                                    }
                            )
                    )
                    .then(
                        ClientCommands.literal("remove")
                            .then(
                                ClientCommands.argument("seconds", IntegerArgumentType.integer(1))
                                    .executes { ctx ->
                                        val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
                                        AnnounceConfig.removeMark(seconds)
                                        ctx.source.sendFeedback(
                                            Component.literal("Removed ${seconds}s.").withStyle(ChatFormatting.YELLOW)
                                        )
                                        1
                                    }
                            )
                    )
                    .then(
                        ClientCommands.literal("list").executes {
                            SafariTracker.printAnnounceMarks(); 1
                        }
                    )
                    .then(
                        ClientCommands.literal("complete")
                            .then(
                                ClientCommands.literal("on").executes { ctx ->
                                    AnnounceConfig.completeToPartyChat = true
                                    ctx.source.sendFeedback(
                                        Component.literal("Biome-complete messages will also post to /pc.")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                            )
                            .then(
                                ClientCommands.literal("off").executes { ctx ->
                                    AnnounceConfig.completeToPartyChat = false
                                    ctx.source.sendFeedback(
                                        Component.literal("Biome-complete messages stay local only.")
                                            .withStyle(ChatFormatting.YELLOW)
                                    )
                                    1
                                }
                            )
                    )
                    .then(
                        ClientCommands.literal("clear").executes { ctx ->
                            AnnounceConfig.clearMarks()
                            ctx.source.sendFeedback(
                                Component.literal("Cleared all announce times.").withStyle(ChatFormatting.YELLOW)
                            )
                            1
                        }
                    )
            )
            .then(
                // Redirects straight to the real /miria command node (not a copy), so
                // "/csafari miria volume 150" and "/miria volume 150" both land on the exact
                // same command and the exact same MiriaConfig.bellVolumePercent setter.
                ClientCommands.literal("miria").redirect(miriaNode)
            )

    private fun buildMiriaCommand(): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("miria")
            .executes { ctx ->
                val seconds = ContestSchedule.secondsUntilNext(java.time.LocalTime.now())
                ctx.source.sendFeedback(
                    Component.literal("Next contest in ${ContestSchedule.formatCountdown(seconds)}")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                )
                1
            }
            .then(
                ClientCommands.literal("hud").executes { ctx ->
                    MiriaConfig.hudEnabled = !MiriaConfig.hudEnabled
                    ctx.source.sendFeedback(
                        Component.literal("Miria HUD ${if (MiriaConfig.hudEnabled) "on" else "off"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("sound").executes { ctx ->
                    MiriaConfig.soundEnabled = !MiriaConfig.soundEnabled
                    ctx.source.sendFeedback(
                        Component.literal("Miria alarm sound ${if (MiriaConfig.soundEnabled) "on" else "off"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("chat").executes { ctx ->
                    MiriaConfig.chatEnabled = !MiriaConfig.chatEnabled
                    ctx.source.sendFeedback(
                        Component.literal("Miria chat message ${if (MiriaConfig.chatEnabled) "on" else "off"}")
                            .withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("partychat").executes { ctx ->
                    MiriaConfig.partyChatEnabled = !MiriaConfig.partyChatEnabled
                    ctx.source.sendFeedback(
                        Component.literal(
                            "Miria party-chat ping ${if (MiriaConfig.partyChatEnabled) "on" else "off"}"
                        ).withStyle(ChatFormatting.YELLOW)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("test").executes { ctx ->
                    ContestTracker.playAlarm()
                    ctx.source.sendFeedback(
                        Component.literal("Test alarm played.").withStyle(ChatFormatting.GRAY)
                    )
                    1
                }
            )
            .then(
                ClientCommands.literal("volume")
                    .executes { ctx ->
                        ctx.source.sendFeedback(
                            Component.literal("Miria bell volume: ${MiriaConfig.bellVolumePercent}%")
                                .withStyle(ChatFormatting.YELLOW)
                        )
                        1
                    }
                    .then(
                        // 0-1000, not 0-100 - same fix as the shiny-sound volume command above.
                        ClientCommands.argument("percent", IntegerArgumentType.integer(0, 1000))
                            .executes { ctx ->
                                val percent = IntegerArgumentType.getInteger(ctx, "percent")
                                MiriaConfig.bellVolumePercent = percent
                                ctx.source.sendFeedback(
                                    Component.literal("Miria bell volume: ${MiriaConfig.bellVolumePercent}%")
                                        .withStyle(ChatFormatting.YELLOW)
                                )
                                1
                            }
                    )
            )
}
