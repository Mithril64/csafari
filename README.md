# Critter Safari Timer

Client-side Fabric mod for Minecraft **26.1.2**, written in Kotlin. Times how long you have
been inside a Critter Safari instance and tracks how many of the 37 critters you have found.
It also alerts you for Miria's contest, which runs on a completely separate schedule (see
below) - bundled into the same mod rather than shipped as a second jar.

## What it tracks

| Stat | Meaning |
| --- | --- |
| Time | Elapsed since you entered the instance, tracked in client ticks rather than wall-clock time (see below) |
| Per biome | Uniques found in Cavern / Forest / Haunted / Icy, colour-coded (orange / green / purple / blue) |
| Missing | Trailing next to each biome, in gray: what's left in that biome |
| Sparkling | Any SPARKLING critters this run |

A capture and a loot share count identically toward every one of these stats - it doesn't
matter whether you or a teammate caught it, only that it's no longer missing for the team.
The only place capture vs. share shows up at all is the chat line announcing it.

## Chat handling

While a run is active, the mod hides the vanilla `CAPTURE!` / `LOOT SHARE!` / `SPARKLING!`
lines and replaces each with a single compact line of its own: `New:` the first time anyone
finds a critter this run, `New (shared):` if a teammate's loot share is what made it new,
and a quieter `Caught:` / `Shared:` line for a repeat. A loot share always names a player, so
it's checked before the new/repeat wording is decided - a teammate's find can never render as
a plain `New:` with no attribution. Anything Hypixel resends within 2 seconds is treated as a
duplicate packet and dropped rather than shown or counted twice. Lines that aren't safari
events, or that arrive while no run is tracked, pass through untouched.

## Commands

Renamed from `/safari` to `/csafari` (it collided with another mod's command) - `/cs` is
registered as a plain alias for `/csafari`, so both work everywhere below interchangeably.

- `/csafari` — summary of the current (or last) run
- `/csafari missing` — what's still missing (by anyone on the team), grouped by biome, one line
- `/csafari pb` — all 4 biomes' fastest-ever completion times plus an all-biomes-complete
  time, and who set each one, saved across game restarts
- `/csafari session` — this session's run count, average and fastest time, and a log of every
  Sparkling critter found and which numbered run it was on (see below)
- `/csafari session reset` — wipe this session's run count, average, PB, and shiny log back
  to zero without restarting the game
- `/csafari start` / `/csafari stop` — manual timer control (also disables auto-detection)
- `/csafari reset` — restart the timer and counters without leaving
- `/csafari hud` — toggle the overlay
- `/csafari esp` — toggle the Hideyho glow-through-walls highlight
- `/csafari paintings` — toggle hiding vanilla Painting entities (hidden by default)
- `/csafari armadillo` — toggle the Armadillo glow-through-walls highlight (on by default,
  only while a safari run is active)
- `/csafari sparkling` — toggle the full-screen rainbow border for nearby Sparkling critters
  (on by default)
- `/csafari particles` — debug command: lists every particle type seen nearby in the last 4
  seconds and how many times, to help confirm the real particle id the Sparkling shiny
  effect uses (see "Sparkling border" below)
- `/csafari announce` — toggle scheduled party-chat posts (on by default); `add`/`remove`/
  `list`/`clear` manage the elapsed-time marks (see below)
- `/csafari announce complete on|off` — also post biome-complete messages to party chat, on
  by default
- `/miria` — time remaining until the next contest
- `/miria hud` — toggle the bottom-left countdown overlay (on by default; the "Miria's
  Contest" title is colored red or green depending on whether this contest's ticket is
  secured - cached for the rest of that contest and persisted across restarts, see below)
- `/miria sound` — toggle the alarm sound (on by default)
- `/miria chat` — toggle the local chat message (on by default)
- `/miria partychat` — toggle posting "Miria's contest is ready!" to party chat on contest
  start, and again whenever someone warps to Torrhus Canyon mid-contest (on by default,
  1-minute cooldown between posts - see below)
- `/miria test` — play the alarm sound immediately, to check it actually fires for you

## Miria's contest

Separate from everything else in this mod: Miria's contest runs on a fixed real-world clock
schedule, every **xx:15, xx:35 and xx:55** of every hour, forever - a 20-minute period that
has nothing to do with Critter Safari runs, game ticks, or the in-game day/night cycle. The
mod reads your system clock directly and, the moment one of those marks hits:

- plays a short alarm sound (a bell "ding", local to you only - it's never broadcast to
  anyone else), unless you've turned it off with `/miria sound`
- sends you a local chat message, unless turned off with `/miria chat`
- a countdown in the bottom-left corner of the screen (`Next in mm:ss`) always ticks down to
  the next one, regardless of whether you're in a safari run - it turns gold in the last 10
  seconds as a visual heads-up. Toggle it with `/miria hud`.

All toggles persist across game restarts (`critter-safari-miria.json` in the config folder),
the same way the safari announce settings do.

**Party-chat ready ping.** With `/miria partychat` on (default), the mod posts "Miria's
contest is ready!" to party chat the moment a contest starts, and again any time it sees a
party-travel broadcast for Torrhus Canyon - a teammate warping in mid-contest, for instance -
so a late party member gets the same nudge without you having to type it yourself. **Both
posts are skipped once your own ticket already reads secured** (green title, see below) -
there's nothing to remind the party about once that's done. Re-posts are capped at once every
60 seconds so a flurry of people warping in back-to-back doesn't spam the party; the scheduled
contest-start post always goes through regardless (ticket status permitting) and resets that
cooldown.

The real line (per your report) is ` » {username} is traveling to Torrhus Canyon FOLLOW`.
Two earlier guesses at matching it - a literal `/warp torrhus` command, then the fuller
phrase "traveling to Torrhus Canyon" - both still didn't fire for you, for reasons I can't
pin down without seeing the exact text my end. Loosened to just the single word "Torrhus"
(case- and position-insensitive) for now - distinctive enough that it's very unlikely to
false-positive on anything else - and added a debug line: **any chat message containing
"Torrhus" is now echoed locally in dark gray, prefixed `[csafari debug] saw:`**, regardless
of whether partychat is on or the ticket's already secured. If the ping still doesn't go out,
that debug line (or its absence) pins down whether detection itself is still missing the
message, or one of the other two gates (the toggle, the ticket-secured check) is suppressing
a detection that did work.

**Ticket secured.** `MiriaTicketDetector` reads the real Hypixel sidebar for the contest's
rarity-tier line (`UNCOMMON with 389`, for example), and the "Miria's Contest" title on the
HUD is colored accordingly - red once a contest starts, green once the tier reads UNCOMMON or
higher. Reuses the same scoreboard-sidebar read `LocationDetector` already uses for safari
detection, so there's no new mixin and no fresh API-mapping risk here.

**Defaults to red (not secured)** rather than showing nothing when it can't yet confirm
either way - right after a restart, before you've glanced at the sidebar this contest, or
simply because the tier is still COMMON. It only turns green once the sidebar has actually
shown UNCOMMON or higher. Since a contest's tier only ever goes up over its fixed 20-minute
window, that result is then cached rather than needing the sidebar line visible every tick:
once green, it stays green for the rest of that window even if you walk away from Torrhus and
the sidebar stops showing contest text. The cache is tagged with which 20-minute window it
belongs to, so it's automatically dropped (back to red) the instant the next contest starts -
nothing links it to `ContestTracker` directly, it just compares the current window's start
time to the one the cache was written for. It's also persisted to disk
(`critter-safari-miria-ticket.json` in the config folder), so restarting the game mid-contest
still remembers "secured" instead of reverting to red until the sidebar's read once more.

Firing is edge-detected per client tick (20 times a second) rather than polled on a slower
timer, so it can't double-fire within the same second and, barring a severe lag spike that
skips a tick straight over the `:00` second of a mark minute, can't miss one either.

## Hideyho ESP

While a run is active, any entity whose disguise name contains "Hideyho" gets the vanilla
glowing outline forced on - the same client-side effect the Glowing status effect uses, which
the renderer draws through blocks. Detection is by the disguise's profile name rather than its
skin texture: Hideyho's wiki entry lists its Mob Type as "Humanoid", meaning it's almost
certainly a fake-player disguise like Hypixel's other Haunted-biome critters, and a name check
is far simpler and more stable than decoding texture bytes.

**This is unverified against a real client** - I don't have a way to confirm Hideyho's exact
disguise name or skin without being in-game myself. If `/csafari esp` doesn't highlight it:
- The disguise name might not literally be "Hideyho" - change `MATCH_NAME` in
  `HideyhoDetector.kt` to whatever name it actually uses (a mod like NameTagTweaks or even
  vanilla F3+B/the tab list can reveal a disguise's real profile name).
- If the mod fails to *build* specifically on `EntityGlowMixin.java`, the method name
  `isCurrentlyGlowing` is the first thing to check against a real 26.1.2 client jar - see the
  comment in that file.

## Armadillo ESP

`/csafari armadillo` toggles the same glow-through-walls highlight as Hideyho ESP, for the
ordinary vanilla Armadillo mob (the one you grind for Wolf Armor scutes) - **on by default**.
It shares `EntityGlowMixin` with Hideyho rather than getting its own mixin: that injection
point has been confirmed working in-game since the Hideyho ESP shipped, so piggybacking on it
avoids the method/package-guessing risk a brand new mixin carries. `ArmadilloDetector` is a
plain `entity is Armadillo` type check (Armadillo is a real mob, not a fake-player disguise
like Hideyho, so there's no name-matching involved), and the class path
(`net.minecraft.world.entity.animal.armadillo.Armadillo`) is confirmed against real
Mojang-mapped output rather than guessed.

**Now scoped to an active safari run** (changed from a previous version of this mod, which
deliberately left it unscoped on the reasoning that Armadillo isn't one of the 37 Critter
Safari critters and the ESP is useful anywhere). Per request, that's been reversed:
`ArmadilloDetector` now checks the same `SafariTracker.currentRun == null` gate
`HideyhoDetector` already used, so it only highlights while a run is in progress.

## Sparkling border

`/csafari sparkling` toggles a pulsing rainbow border around the screen edge, plus a label
naming which Sparkling critter(s) triggered it, whenever one is nearby - **on by default**,
scoped to an active safari run. The point is visibility: a chat line is easy to miss
mid-fight or while looking at the HUD, a full-screen border isn't.

Detection (`SparklingHighlighter`) scans nearby entities every 5 ticks (4 times a second) two
different ways, since neither alone catches everything:

1. **Nametag text.** Checks for "SPARKLING" in a fake-player disguise's GameProfile name or a
   plain custom-name component (the same two places `HideyhoDetector` checks for Hideyho).
   When this matches, the "SPARKLING" marker is stripped out and the remainder is resolved
   against the real critter list (`Critters.matchAtStart`) for the name shown on screen.
2. **Particle fallback.** Per your report, some Sparkling critters don't have "SPARKLING" in
   their nametag at all - just the sparkle/glint particle effect around them. For any entity
   whose plain name resolves to a real critter but whose nametag didn't match channel 1, this
   checks `ParticleTracker` (backed by a new mixin into `ClientLevel.addParticle` that records
   every particle the client spawns, with position and timestamp) for a shiny-looking particle
   recorded within ~2.5 blocks of it in the last 1.5 seconds.

**The particle type id is now confirmed, not guessed.** The original guess list
(`totem_of_undying`, `end_rod`, `firework`, `witch`, `enchant`, `crit`) was too broad -
`end_rod` in particular was false-flagging on unrelated particles, per your report.
`SparklingHighlighter.SHINY_PARTICLE_HINTS` now contains just the one real id you confirmed
with `/csafari particles`: `wax_on` (the same particle vanilla uses for an axe un-waxing
copper, evidently reused by Hypixel's resource pack for the sparkle glint). `/csafari
particles` is still there as a debug command if a future server update ever changes it again.
`ParticleWatcherMixin` lives in the optional mixin config, so if its target doesn't exist on
your build, this fallback (and the debug command) just silently does nothing rather than
breaking anything else.

**Build note:** the first version of this shipped with a real compile error - Java calling a
Kotlin `object`'s method (`ParticleTracker.record(...)` from `ParticleWatcherMixin.java`)
needs `@JvmStatic` on the Kotlin side, otherwise it's only reachable as an instance method
(`ParticleTracker.INSTANCE.record(...)`) and `javac` rejects the static-looking call. Fixed by
adding `@JvmStatic` to `ParticleTracker`'s functions.

A new **shiny alert sound** (`csafari:shiny`, a short clip converted to Ogg Vorbis) plays
once whenever a Sparkling critter newly starts matching - tracked by entity id so it won't
re-fire every scan while the same critter stays in range, and only fires again for a
genuinely new one.

The border itself (`SparklingBorderOverlay`) is drawn as many thin segments around the
perimeter, each offset in hue by its position plus the current time, so the whole ring
visibly rotates rather than just flashing one color. It uses `graphics.fill(x1, y1, x2, y2,
color)` - the long-stable vanilla name for a plain filled rectangle - and the name label uses
`graphics.text(...)`, the same text-drawing method already confirmed working elsewhere in
this mod (`SafariHud`, `MiriaHud`), rather than an unverified `drawCenteredString` guess.
`fill` itself is still unverified against this exact build - if the mod fails to build
specifically on `SparklingBorderOverlay.kt`, that's the first thing to check.

**Is this detectable by the server?** No more than the chat-hiding or HUD features already
in this mod, and meaningfully less risky than the Hideyho/Armadillo ESP above. Those force
an entity's glow-through-walls flag, which changes what the *renderer* does with
already-synced entity data in a way a server-side anti-cheat could at least theoretically
infer from reaction patterns (seeing/reacting to something before it's in view). This
feature does neither: it reads a nametag string that's already being sent to your client and
already visible on your screen the normal way, and draws a border - no new information is
revealed, nothing about how entities render changes, and nothing is sent to the server at
all. It's in the same risk category as the existing chat-replacement and HUD timer, not the
same category as the ESP.

## Hidden paintings

`/csafari paintings` toggles whether vanilla Painting entities render at all - **hidden by
default**. `PaintingRenderMixin` hides them via `EntityRenderDispatcher.shouldRender(...)`
while `PaintingHider.enabled` is true - the dispatcher-level gate that decides whether an
entity reaches a renderer at all, returning `false` for any `Painting`. It's a pure
client-side visual skip: nothing about the painting's actual block or entity data changes,
and other players still see it normally. This is unrelated to Critter Safari or Miria's
contest - it's a general declutter toggle, on by default per your request; flip it back with
the command if you want paintings visible again.

**This mixin went through two wrong guesses before landing here, which is worth knowing if
it ever needs touching again:**
1. The first version targeted `PaintingRenderer.render(Painting, ...)`. That signature is
   pre-1.20.5: the "render state" refactor changed `render()` to take an extracted
   `PaintingRenderState` instead of the entity itself, so the old target silently failed to
   match (and possibly hit a synthetic bridge-method ambiguity from the generic override on
   top of that).
2. It was also originally bundled into the same `"required": true` mixin config as
   `EntityGlowMixin` (the proven-working Hideyho ESP). A `"required": true` mixin whose
   target can't be found is fatal to Fabric's loader - it crashed the whole game at launch,
   taking the working ESP mixin down with it even though that one was never the problem.
   Fixed by splitting it into its own `csafari-optional.mixins.json`, marked
   `"required": false`, so a bad guess here now just logs a warning and no-ops instead of
   crashing anything.
3. The current version instead targets `EntityRenderDispatcher.shouldRender(Entity, Frustum,
   double, double, double)` - a dispatcher-level gate method that hasn't been touched by the
   render-state refactor and isn't a generic override (so no bridge-method risk), verified
   against real Mojang-mapped output rather than guessed.
4. `Painting` itself also turned out to have moved packages along the way - from
   `world.entity.decoration.Painting` to `world.entity.decoration.painting.Painting`,
   sometime after 1.21.6 - caught as a `compileJava` error rather than a silent runtime
   no-op, and fixed the same way: checked against mappings.dev instead of guessed again.

Both method target and package are now confirmed against real Mojang-mapped data, but one
version short of 26.1.2 itself - so if `/csafari paintings` still doesn't hide anything once
it builds clean, check the game log for a Mixin warning naming `PaintingRenderMixin`; the fix
at that point is a small name/package tweak against this exact build, not re-architecting
the approach again.

## Scheduled party-chat announcements

`/csafari announce add 120` schedules an automatic post at 120 seconds into the run, in the
format `/pc Cavern: A, B | Forest: C | Haunted: D, E`, listing only biomes that still have
something missing (skipped entirely if the team already has full dex). Add as many marks as
you want; `/csafari announce list` shows them, `/csafari announce remove <seconds>` /
`clear` remove them. `/csafari announce` (no arguments) toggles the whole feature - on by
default. Each mark fires once per run. Party-chat posts never include usernames, whether
it's the missing list or a biome-complete post; `/csafari pb` is the only place player
attribution shows up, since that's local-only.

## Personal bests

The first time you complete a biome, its time and who completed it (you, or the teammate
whose share finished it) are saved to `critter-safari-bests.json` in your Fabric config
folder, shown as pink "New PB!" - every time after, the completion message shows how it
compares ("Best: 11:58 - You"). A single biome's PB still credits just whoever finished it,
same as always.

Completing all 4 biomes in one run tracks its own separate "ALL biomes complete!" PB the same
way, saved to `critter-safari-bests-all.json` - but **this one now credits everyone who
contributed a find that run**, not just whoever happened to catch the one critter that tipped
it over to "complete". A full-dex clear is a team effort, and the PB used to only list one of
up to 4 party members; now it's every name that showed up on a capture, a loot share, or a
SPARKLING broadcast this run, joined together ("You, Steve, Alex, Notch").
`/csafari pb` lists all four biomes' times plus the all-biomes time, and who set each one.
`/csafari announce complete off` stops biome-complete (and all-biomes-complete) messages from
also posting to party chat (time only, no username in either place) - on by default, since
the point is your team seeing it.

## Forest counts as complete without Macaw

Macaw (Forest's legendary) doesn't count toward Forest's completion total or "complete"
trigger - realistically requiring a legendary before a biome counts as done isn't a practical
goal. So Forest can announce as complete, and Forest's found/total count can read 8/8, before
you've actually caught it.

**It does still show up in the missing list** (both `/csafari missing` and the party-chat
announcer) until you actually catch it - that's deliberate and separate from the
completion/total carve-out above: "doesn't block completion" and "disappears from what's
missing" used to be the same thing here, which meant Macaw would vanish from the missing list
the moment the rest of Forest was done, even though you still hadn't caught it. Now it keeps
showing as missing right up until you do.

It's fully tracked everywhere else regardless: if you do catch it, it still adds to the
overall dex count and still gets its own "New: Macaw" line. The same carve-out isn't applied
to the other biomes' legendaries (Doomspiral, Wumpa) since only Macaw was asked for - say the
word if you want those excluded too.

## Building

This version of Minecraft is unobfuscated, so the build differs from older Fabric projects:
the `net.fabricmc.fabric-loom` plugin (not `fabric-loom-remap`), no `mappings` dependency,
plain `implementation` instead of `modImplementation`, `jar` instead of `remapJar`, Java 25.

You need **JDK 25** installed. The Gradle wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`) is included, so no separate Gradle install is needed:

```sh
./gradlew build
```

```powershell
.\gradlew.bat build
```

Output lands in `build/libs/` — drop that jar in `.minecraft/mods/`. Needs Fabric Loader
0.19.3+, Fabric API for 26.1.2, and Fabric Language Kotlin 1.13.13+ alongside it.

No JDK 25 handy? Push the folder to a GitHub repo — `.github/workflows/build.yml` builds it
on every push and attaches the jar as a downloadable artifact.

Pin the exact Fabric API build in `gradle.properties` — check the Fabric Develop site for
the current 26.1.2 release before your first build.

## How critters are detected

Chat lines are matched in fixed grammatical slots, then validated against the 37-critter list:

```
CAPTURE! You caught a Hideonwall and gained a Hideonwall Shard!
LOOT SHARE! You received 2x Billygoat Shard from Xispana catching a Billygoat!
SPARKLING! Xispana caught a SPARKLING Foxtrot!
```

The name is only ever read from after `You caught a` or `catching a`, never from anywhere
else in the line, so usernames cannot be mistaken for critters — even a player named
`Billygoat`.

## 26.1.2 API renames used here

Minecraft 26.1.2 went unobfuscated and Fabric API renamed several classes to match the
official names. This project already uses the post-rename names:

| Old (pre-26.1) | New (26.1.2) |
| --- | --- |
| `GuiGraphics` | `GuiGraphicsExtractor` |
| `graphics.drawString(...)` | `graphics.text(font, text, x, y, color, shadow)` |
| `ResourceLocation` | `Identifier` |
| `Player.displayClientMessage(...)` | `Player.sendSystemMessage(...)` |
| `ClientCommandManager` | `ClientCommands` |

If a future Fabric API bump renames something again, the full list is at
[docs.fabricmc.net/develop/porting/fabric-api](https://docs.fabricmc.net/develop/porting/fabric-api).

## Session average / PB

Separate from the persisted per-biome bests, this tracks every run you actually play out this
session, in memory only - it resets every game launch, not just every world join.

**Previously this only counted a run if you caught all 37 critters** - every other run (35/37,
say) was silently ignored, which undercounted the session total. Fixed: a full-dex run is
still recorded the instant all 4 biomes finish, using the elapsed time at that exact moment
(not whenever the run/instance later ends - lingering in the lobby afterward shouldn't inflate
that run's time). Any run that doesn't clear all 4 biomes is now instead recorded when it
ends, using however long it actually ran. Either way a run is only ever counted once.

`/csafari session` shows the average and fastest counted time this session; it also shows
automatically at the bottom of `/csafari` and the end-of-run summary, and the average is
pinned to the top-right of the screen while a run is active, once at least one run has
counted. A run under 60 seconds doesn't count toward any of these: Critter Safari runs for a
fixed length, so anything detected as finished that fast is almost certainly a false reading
(a sidebar flicker, a stale run that never reset) rather than a real one, and counting it
would just drag the average down with a data point that was never genuine.

Every run started this session also gets a plain 1-up number (`SafariRun.runNumber`, kept by
`SafariTracker`, independent of whether that run ends up "counted" above) - its only job is
giving each run a short, memorable label. Any Sparkling critter found - your own capture, a
teammate's loot share, or just the SPARKLING broadcast line, whichever happens first - gets
logged once per critter per run against that number, and `/csafari session` lists them all:
`Shinies: run 13 "Parakeet", run 49 "Troodon"`. `/csafari session reset` clears the run count,
average, PB, and this shiny log back to zero without needing to restart the game.

## Tick-based timer

Elapsed time counts client ticks (SafariTracker.tick() incrementing SafariRun.tick(), called
from ClientTickEvents.END_CLIENT_TICK) rather than System.currentTimeMillis(). A tick is
nominally 50ms at 20 TPS. The difference only shows up during a client-side lag spike or
freeze: since tick processing stalls right along with everything else during a freeze,
wall-clock time would keep advancing through it and inflate the timer, while tick-based time
simply doesn't advance until ticks resume - closer to what actually happened in-game rather
than how much real-world time passed while the client was stuck.

## Known soft spots

- **Instance detection** reads the SkyBlock sidebar for "Critter Safari". If Hypixel renames
  the line, auto-start breaks; `/csafari start` still works. Adjust `SAFARI_MARKER` in
  `LocationDetector.kt`.
- Exact capture-message wording is taken from the wiki. If a line does not register,
  log what you actually receive and loosen the regex in `ChatParser.kt`.
- **Consecutive instances** (queued straight into a fresh Critter Safari with the sidebar
  never showing "not in safari" in between) are caught by comparing `mc.level` identity each
  tick, not by parsing any Hypixel-specific "lobby ID" text - every genuine instance switch
  constructs a new `ClientLevel` object even when the new instance looks identical to the
  old one, so this doesn't depend on unverified sidebar formatting. If it turns out
  consecutive safari instances *don't* actually swap the level object, this won't catch it;
  the fallback would be an actual sidebar-based lobby ID, which needs a real example of what
  changes between two consecutive instances to build correctly (same as the Hideyho fix).
- **Miria alarm sound**: an earlier version of this referenced a `SoundEvents.XXX` constant
  for the bell's "ding" and guessed wrong twice (`BELL_BLOCK_USE`, then `BLOCK_BELL_USE` -
  neither exists on this build). Fixed by building the `SoundEvent` directly from its
  resource-location id (`"block.bell.use"`, via `SoundEvent.createVariableRangeEvent`)
  instead of guessing a Java constant name - the id is documented game data, not an
  obfuscation-prone identifier, so it's much less likely to have moved. If the alarm is
  silent in-game despite the mod building fine, that id itself is the thing to double-check
  against the current sound list.
- **Miria's contest schedule** (xx:15/35/55) is read from your system's local clock, on the
  assumption that's what the contest is actually synced to. If Hypixel runs it off a
  different timezone than your system's, the alarm will fire at a consistent offset from the
  real start rather than exactly on it - adjust `PHASE_SEC`/`PERIOD_SEC` in
  `ContestSchedule.kt` if you find that's the case.
