package com.saraltechies.saralfilemanager.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test fun waitsForAFewGoodMoments() {
        assertFalse(ReviewPolicy.shouldAsk(goodMoments = 2, promptsShown = 0, lastPromptAtMs = 0, nowMs = now))
        assertTrue(ReviewPolicy.shouldAsk(goodMoments = 3, promptsShown = 0, lastPromptAtMs = 0, nowMs = now))
    }

    @Test fun waitsThirtyDaysBetweenPrompts() {
        assertFalse(ReviewPolicy.shouldAsk(10, 1, lastPromptAtMs = now - 29 * day, nowMs = now))
        assertTrue(ReviewPolicy.shouldAsk(10, 1, lastPromptAtMs = now - 30 * day, nowMs = now))
    }

    @Test fun stopsAfterThreePrompts() {
        assertFalse(ReviewPolicy.shouldAsk(100, 3, lastPromptAtMs = now - 365 * day, nowMs = now))
    }
}
