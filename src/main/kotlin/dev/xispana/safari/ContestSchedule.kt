package dev.xispana.safari

import java.time.LocalTime

/**
 * Miria's contest runs on a fixed real-world wall-clock schedule: xx:15, xx:35, xx:55, every
 * hour, forever. That's a period of exactly 20 minutes (1200 seconds), offset by 900 seconds
 * (15 minutes) from the top of the hour - and since an hour (3600s) divides evenly into three
 * 1200s blocks, that period holds steady across hour and day boundaries with no special-casing
 * needed. Everything here reads the system clock directly (no reliance on game ticks or the
 * in-game day/night cycle), since the contest is tied to real time, not Minecraft time.
 */
object ContestSchedule {

    private const val PERIOD_SEC = 1200L // 20 minutes
    private const val PHASE_SEC = 900L   // :15 past the hour is the first mark of each period

    val MARK_MINUTES: Set<Int> = setOf(15, 35, 55)

    /** Seconds from `now` until the next contest start (0 if `now` lands exactly on one). */
    fun secondsUntilNext(now: LocalTime): Long {
        val sec = now.toSecondOfDay().toLong()
        val phase = ((sec - PHASE_SEC) % PERIOD_SEC + PERIOD_SEC) % PERIOD_SEC
        return if (phase == 0L) 0L else PERIOD_SEC - phase
    }

    /** Seconds since the most recent contest start (0 if `now` lands exactly on one). */
    fun secondsSinceLastMark(now: LocalTime): Long {
        val sec = now.toSecondOfDay().toLong()
        return ((sec - PHASE_SEC) % PERIOD_SEC + PERIOD_SEC) % PERIOD_SEC
    }

    fun formatCountdown(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
