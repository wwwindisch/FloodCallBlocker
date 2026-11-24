package app.floodcallblocker.core

/**
 * Pure decision logic ("let through / block / start block"). No Android, no I/O.
 *
 * Flow (see SPEC F-22):
 * 0. N == 0 → always block.
 * 1. Temporary block running → block, do not count, do not extend.
 * 2. Record the call, count calls in the sliding window (including this one).
 * 3. count > N → start block, discard counted calls, block this call.
 *    Otherwise let it through.
 */
object CallEngine {
    fun evaluate(
        rule: Rule,
        state: EntryState,
        now: Long,
    ): Evaluation {
        if (rule.alwaysBlocked) {
            return Evaluation(Verdict.BLOCK_ALWAYS, state.copy(blockedUntil = null, callTimes = emptyList()))
        }

        val until = state.blockedUntil
        if (until != null && now < until) {
            return Evaluation(Verdict.BLOCK_ACTIVE, state)
        }

        // Sliding window: only calls strictly newer than (now - window) count.
        val windowStart = now - rule.windowMillis
        val inWindow = state.callTimes.filter { it > windowStart && it <= now } + now
        return if (inWindow.size > rule.allowedCalls) {
            Evaluation(
                verdict = Verdict.BLOCK_TRIGGERED,
                newState = EntryState(blockedUntil = now + rule.blockDurationMillis, callTimes = emptyList()),
                triggerCount = inWindow.size,
            )
        } else {
            Evaluation(Verdict.ALLOW, EntryState(blockedUntil = null, callTimes = inWindow))
        }
    }

    /** True if a temporary block is running at [now]. */
    fun isBlockActive(
        state: EntryState,
        now: Long,
    ): Boolean = state.blockedUntil?.let { now < it } ?: false
}
