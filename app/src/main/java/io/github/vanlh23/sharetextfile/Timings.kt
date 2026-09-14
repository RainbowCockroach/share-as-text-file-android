package io.github.vanlh23.sharetextfile

import java.time.Duration

/** Deletion delays. Build with `-PfastTimers=true` for short values when testing on a device. */
object Timings {
    val SAFETY_TTL: Duration = Duration.ofMillis(BuildConfig.SAFETY_TTL_MS)
    val GRACE_AFTER_CHOICE: Duration = Duration.ofMillis(BuildConfig.GRACE_AFTER_CHOICE_MS)
    val CANCEL_DELAY: Duration = Duration.ofMillis(BuildConfig.CANCEL_DELAY_MS)
}
