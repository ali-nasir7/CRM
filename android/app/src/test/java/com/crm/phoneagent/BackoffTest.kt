package com.crm.phoneagent

import com.crm.phoneagent.network.nextBackoffDelayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reconnect pacing must grow, then cap - never a hot loop, never an unbounded wait. */
class BackoffTest {

    @Test
    fun growsExponentiallyForEarlyAttempts() {
        assertEquals(2000L, nextBackoffDelayMs(1))
        assertEquals(4000L, nextBackoffDelayMs(2))
        assertEquals(8000L, nextBackoffDelayMs(3))
        assertEquals(16000L, nextBackoffDelayMs(4))
    }

    @Test
    fun capsAtThirtySeconds() {
        assertEquals(30_000L, nextBackoffDelayMs(5))
        assertEquals(30_000L, nextBackoffDelayMs(6))
        assertEquals(30_000L, nextBackoffDelayMs(100))
    }

    @Test
    fun neverZeroOrNegativeForAnyAttempt() {
        for (attempt in -5..50) {
            assertTrue(nextBackoffDelayMs(attempt) > 0)
        }
    }
}
