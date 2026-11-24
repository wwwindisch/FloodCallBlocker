package app.floodcallblocker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEngineTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val rule = Rule(allowedCalls = 2, windowMinutes = 5, blockDurationMinutes = 180)

    /** Feeds calls (minutes after 10:00) through the engine, returning the verdicts. */
    private fun run(
        rule: Rule,
        vararg minutesAfterTen: Int,
    ): Pair<List<Verdict>, EntryState> {
        var state = EntryState()
        val verdicts =
            minutesAfterTen.map {
                val e = CallEngine.evaluate(rule, state, it * minute)
                state = e.newState
                e.verdict
            }
        return verdicts to state
    }

    @Test
    fun `spec table from F-22`() {
        // 10:00 10:03 10:09 10:10 10:12 11:30 13:15 as minutes after 10:00
        val (v, state) = run(rule, 0, 3, 9, 10, 12, 90, 195)
        assertEquals(
            listOf(
                Verdict.ALLOW,
                Verdict.ALLOW,
                Verdict.ALLOW,
                Verdict.ALLOW,
                Verdict.BLOCK_TRIGGERED,
                Verdict.BLOCK_ACTIVE,
                Verdict.ALLOW,
            ),
            v,
        )
        assertNull(state.blockedUntil)
        assertEquals(listOf(195 * minute), state.callTimes)
    }

    @Test
    fun `block runs exactly until 13_12 and is not extended`() {
        var state = EntryState()
        listOf(0, 3, 9, 10).forEach { state = CallEngine.evaluate(rule, state, it * minute).newState }
        val trigger = CallEngine.evaluate(rule, state, 12 * minute)
        assertEquals(Verdict.BLOCK_TRIGGERED, trigger.verdict)
        assertEquals(3, trigger.triggerCount)
        assertEquals(12 * minute + 3 * hour, trigger.newState.blockedUntil)
        assertTrue(trigger.newState.callTimes.isEmpty())

        val during = CallEngine.evaluate(rule, trigger.newState, 90 * minute)
        assertEquals(Verdict.BLOCK_ACTIVE, during.verdict)
        assertEquals(trigger.newState, during.newState)
    }

    @Test
    fun `call exactly at block end passes`() {
        val state = EntryState(blockedUntil = 1000L)
        assertEquals(Verdict.ALLOW, CallEngine.evaluate(rule, state, 1000L).verdict)
        assertEquals(Verdict.BLOCK_ACTIVE, CallEngine.evaluate(rule, state, 999L).verdict)
    }

    @Test
    fun `N equals zero blocks always and keeps no state`() {
        val r = rule.copy(allowedCalls = 0)
        val (v, state) = run(r, 0, 1, 1000)
        assertTrue(v.all { it == Verdict.BLOCK_ALWAYS })
        assertEquals(EntryState(), state)
    }

    @Test
    fun `N equals zero clears a running temporary block`() {
        val e = CallEngine.evaluate(rule.copy(allowedCalls = 0), EntryState(blockedUntil = 10 * hour), 1L)
        assertNull(e.newState.blockedUntil)
    }

    @Test
    fun `N equals one second call triggers`() {
        val (v, _) = run(rule.copy(allowedCalls = 1), 0, 1)
        assertEquals(listOf(Verdict.ALLOW, Verdict.BLOCK_TRIGGERED), v)
    }

    @Test
    fun `N equals one second call after window passes`() {
        val (v, _) = run(rule.copy(allowedCalls = 1), 0, 6)
        assertEquals(listOf(Verdict.ALLOW, Verdict.ALLOW), v)
    }

    @Test
    fun `large N needs N plus one calls`() {
        val r = rule.copy(allowedCalls = 50, windowMinutes = 60)
        val times = IntArray(51) { it }
        val (v, _) = run(r, *times)
        assertTrue(v.take(50).all { it == Verdict.ALLOW })
        assertEquals(Verdict.BLOCK_TRIGGERED, v.last())
    }

    @Test
    fun `window is sliding and boundary call is dropped`() {
        // Calls at 0 and 5: the one at 0 is exactly window old → no longer counted.
        val (v, state) = run(rule, 0, 1, 5)
        assertEquals(listOf(Verdict.ALLOW, Verdict.ALLOW, Verdict.ALLOW), v)
        assertEquals(listOf(1 * minute, 5 * minute), state.callTimes)
    }

    @Test
    fun `calls during block do not count after it ends`() {
        val r = rule.copy(allowedCalls = 1, blockDurationMinutes = 10)
        val (v, state) = run(r, 0, 1, 2, 3, 4, 11)
        assertEquals(
            listOf(
                Verdict.ALLOW,
                Verdict.BLOCK_TRIGGERED,
                Verdict.BLOCK_ACTIVE,
                Verdict.BLOCK_ACTIVE,
                Verdict.BLOCK_ACTIVE,
                Verdict.ALLOW,
            ),
            v,
        )
        assertEquals(listOf(11 * minute), state.callTimes)
    }

    @Test
    fun `isBlockActive`() {
        assertTrue(CallEngine.isBlockActive(EntryState(blockedUntil = 10), 9))
        assertFalse(CallEngine.isBlockActive(EntryState(blockedUntil = 10), 10))
        assertFalse(CallEngine.isBlockActive(EntryState(), 10))
    }

    @Test
    fun `rule coercion`() {
        val r = Rule(allowedCalls = -3, windowMinutes = 0, blockDurationMinutes = 99999999).coerced()
        assertEquals(0, r.allowedCalls)
        assertEquals(1, r.windowMinutes)
        assertEquals(Rule.MAX_BLOCK_MINUTES, r.blockDurationMinutes)
        assertEquals(999, Rule(allowedCalls = 5000).coerced().allowedCalls)
    }
}
