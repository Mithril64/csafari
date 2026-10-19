package dev.xispana.safari

import org.slf4j.LoggerFactory

/**
 * Best-effort "are we currently in a Hypixel party" tracker, built from watching the server's
 * own party-system chat lines - Hypixel doesn't expose a party-membership API to the client,
 * so there's nothing more direct to read.
 *
 * Defaults to **not** in a party until actual join evidence is seen. That's the safe default:
 * a wrong "yes" means a `/pc` send silently fails server-side (Hypixel just replies "You are
 * not currently in a party!" to you, nobody else sees anything) - whereas a wrong "no" just
 * costs a ping that doesn't go out, which is obvious and harmless by comparison.
 *
 * Your log caught the first real bug here: the guessed JOINED-message wording below never
 * actually matched anything, so `inParty` stayed stuck at false even while you were plainly
 * in a party (you were seeing the Torrhus travel broadcast at all, which Hypixel only sends to
 * fellow party members in the first place). TRAVEL_BROADCAST below fixes that using exactly
 * the evidence your log gave: " » Acrohh is traveling to Private Island FOLLOW" is the same
 * broadcast shape as the Torrhus line, just a different destination - any line matching that
 * general "is traveling to ... FOLLOW" shape is, by itself, proof positive you're in a party,
 * confirmed against real text rather than guessed like JOINED/LEFT still are below.
 */
object PartyTracker {

    private val LOGGER = LoggerFactory.getLogger("csafari-party")

    @Volatile
    var inParty: Boolean = false
        private set

    // Confirmed against your log: any "{username} is traveling to {destination} FOLLOW" line,
    // not just the Torrhus one, since Hypixel only broadcasts these to the traveling player's
    // own party. Seeing one at all is conclusive proof of being in a party right now.
    private val TRAVEL_BROADCAST = Regex("(?i)is\\s+traveling\\s+to\\s+.+\\s+FOLLOW")

    // Lines meaning "you're now in a party" - creating one, joining one, or an invite landing.
    // Unlike TRAVEL_BROADCAST above, this wording is still a guess (not yet confirmed against
    // anything you've actually sent me) - if it's not catching a real join, paste the line.
    private val JOINED = Regex(
        "(?i)(you have joined|you'll be partying with|you have created a party|invite has been accepted)"
    )

    // Lines meaning "you're no longer in a party" - including Hypixel's own error reply to a
    // party-only command when there isn't one, which doubles as a self-correcting signal if
    // JOINED above ever mismatches. Also still an unconfirmed guess.
    private val LEFT = Regex(
        "(?i)(you have left the party|you left the party|you have been removed from the party|" +
            "the party was disbanded|you have disbanded the party|you are not currently in a party)"
    )

    // Same colour-code strip ChatParser/ContestTracker use - Hypixel bakes §-codes directly
    // into these lines too, so matching rawLine unstripped would miss them the same way the
    // Torrhus line was missed before this was added.
    private val FORMAT_CODE = Regex("§.")

    fun handleChatLine(rawLine: String) {
        val line = FORMAT_CODE.replace(rawLine, "")
        when {
            TRAVEL_BROADCAST.containsMatchIn(line) -> setInParty(true, line)
            LEFT.containsMatchIn(line) -> setInParty(false, line)
            JOINED.containsMatchIn(line) -> setInParty(true, line)
        }
    }

    private fun setInParty(value: Boolean, cause: String) {
        if (inParty == value) return
        inParty = value
        LOGGER.info("[csafari] party state -> {} (matched: {})", value, cause)
    }
}
