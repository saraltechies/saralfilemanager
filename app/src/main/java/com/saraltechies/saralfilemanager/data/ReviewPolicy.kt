package com.saraltechies.saralfilemanager.data

/**
 * When to show Google Play's in-app review sheet. Play never tells the app whether someone rated it, and
 * asking repeatedly is against policy, so: only after a few good moments (files copied, moved or zipped),
 * at most every [MIN_DAYS_BETWEEN] days, and never more than [MAX_PROMPTS] times. Play applies its own quota on top.
 */
object ReviewPolicy {
    const val MIN_GOOD_MOMENTS = 3
    const val MIN_DAYS_BETWEEN = 30
    const val MAX_PROMPTS = 3
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun shouldAsk(goodMoments: Int, promptsShown: Int, lastPromptAtMs: Long, nowMs: Long): Boolean =
        goodMoments >= MIN_GOOD_MOMENTS &&
            promptsShown < MAX_PROMPTS &&
            (lastPromptAtMs == 0L || nowMs - lastPromptAtMs >= MIN_DAYS_BETWEEN * DAY_MS)
}
