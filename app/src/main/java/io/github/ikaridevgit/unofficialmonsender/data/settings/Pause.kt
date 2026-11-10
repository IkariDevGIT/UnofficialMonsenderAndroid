package io.github.ikaridevgit.unofficialmonsender.data.settings

import java.util.Calendar

enum class PauseUnit(val millis: Long) {
    SECONDS(1_000L),
    MINUTES(60_000L),
    HOURS(3_600_000L),
    DAYS(86_400_000L),
    MONTHS(0L),
}

/**
 * A pause of copy detection. Start and end are wall-clock times, so a pause carries on across app
 * and phone restarts and ends on time even if the phone was off when it ran out. [unit] is null
 * for a pause that lasts until it is lifted by hand; [amount] and [unit] are kept to show what was chosen.
 */
data class Pause(val startedAt: Long, val until: Long, val amount: Int, val unit: PauseUnit?) {
    val indefinite: Boolean get() = unit == null

    fun isActive(now: Long): Boolean = indefinite || now < until

    fun remaining(now: Long): Long = if (indefinite) Long.MAX_VALUE else (until - now).coerceAtLeast(0L)

    /** How much of the pause has passed, from 0 to 1; always 0 for an open-ended one. */
    fun progress(now: Long): Float {
        if (indefinite || until <= startedAt) return 0f
        return ((now - startedAt).toFloat() / (until - startedAt)).coerceIn(0f, 1f)
    }

    companion object {
        val PRESETS = listOf(
            15 to PauseUnit.MINUTES,
            30 to PauseUnit.MINUTES,
            1 to PauseUnit.HOURS,
            8 to PauseUnit.HOURS,
            1 to PauseUnit.DAYS,
        )
        const val QUICK_MINUTES = 30
        const val MAX_AMOUNT = 999

        fun of(amount: Int, unit: PauseUnit, now: Long = System.currentTimeMillis()): Pause {
            val until = if (unit == PauseUnit.MONTHS) {
                Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.MONTH, amount)
                }.timeInMillis
            } else {
                now + amount * unit.millis
            }
            return Pause(now, until, amount, unit)
        }

        fun indefinite(now: Long = System.currentTimeMillis()) = Pause(now, Long.MAX_VALUE, 0, null)

        fun quick(now: Long = System.currentTimeMillis()) = of(QUICK_MINUTES, PauseUnit.MINUTES, now)
    }
}
