package app.floodcallblocker.core

/** What happens to a call while a number is blocked. */
enum class BlockAction { REJECT, SILENCE }

/**
 * The rule of a monitored entry.
 *
 * @property allowedCalls how many calls within [windowMinutes] pass; 0 means "always blocked"
 * @property windowMinutes size of the sliding window in minutes
 * @property blockDurationMinutes how long the temporary block lasts
 */
data class Rule(
    val allowedCalls: Int = DEFAULT_ALLOWED_CALLS,
    val windowMinutes: Int = DEFAULT_WINDOW_MINUTES,
    val blockDurationMinutes: Int = DEFAULT_BLOCK_MINUTES,
    val action: BlockAction = BlockAction.REJECT,
) {
    val alwaysBlocked: Boolean get() = allowedCalls == 0

    val windowMillis: Long get() = windowMinutes * MINUTE_MILLIS
    val blockDurationMillis: Long get() = blockDurationMinutes * MINUTE_MILLIS

    /** Returns this rule with every value clamped into its allowed range. */
    fun coerced(): Rule =
        copy(
            allowedCalls = allowedCalls.coerceIn(0, MAX_ALLOWED_CALLS),
            windowMinutes = windowMinutes.coerceIn(MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES),
            blockDurationMinutes = blockDurationMinutes.coerceIn(MIN_BLOCK_MINUTES, MAX_BLOCK_MINUTES),
        )

    companion object {
        const val DEFAULT_ALLOWED_CALLS = 2
        const val DEFAULT_WINDOW_MINUTES = 5
        const val DEFAULT_BLOCK_MINUTES = 3 * 60

        const val MAX_ALLOWED_CALLS = 999
        const val MIN_WINDOW_MINUTES = 1
        const val MAX_WINDOW_MINUTES = 1440
        const val MIN_BLOCK_MINUTES = 1
        const val MAX_BLOCK_MINUTES = 7 * 24 * 60

        const val MINUTE_MILLIS = 60_000L
    }
}

/**
 * Mutable-per-call state of one entry, persisted by the app.
 *
 * @property blockedUntil epoch millis until which a temporary block lasts, or null
 * @property callTimes epoch millis of counted calls (only the ones in the current window matter)
 */
data class EntryState(
    val blockedUntil: Long? = null,
    val callTimes: List<Long> = emptyList(),
)

/** Outcome of screening a single incoming call. */
enum class Verdict {
    /** Call passes and was counted. */
    ALLOW,

    /** Rule has N = 0: the entry is permanently blocked. */
    BLOCK_ALWAYS,

    /** A temporary block is running; the call is not counted and does not extend the block. */
    BLOCK_ACTIVE,

    /** This call exceeded the limit: a new block starts and this call is blocked already. */
    BLOCK_TRIGGERED,
}

/**
 * Result of [CallEngine.evaluate].
 *
 * @property newState state to persist
 * @property triggerCount number of calls that triggered the block (only for [Verdict.BLOCK_TRIGGERED])
 */
data class Evaluation(
    val verdict: Verdict,
    val newState: EntryState,
    val triggerCount: Int = 0,
) {
    val isBlocked: Boolean get() = verdict != Verdict.ALLOW
}
