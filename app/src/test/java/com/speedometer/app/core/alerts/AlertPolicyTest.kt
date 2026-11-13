package com.speedometer.app.core.alerts

import com.speedometer.app.core.trip.GuardLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    @Test fun firesOnceWhenFirstOverTheLimit() {
        val p = AlertPolicy()
        assertFalse(p.shouldFire(GuardLevel.Off, 0))
        assertTrue(p.shouldFire(GuardLevel.Soft, 1_000))
        assertFalse(p.shouldFire(GuardLevel.Soft, 2_000))
        assertFalse(p.shouldFire(GuardLevel.Soft, 5_000))
    }

    @Test fun repeatsEveryFiveSecondsWhileOver() {
        val p = AlertPolicy()
        val fired = (0..40).count { p.shouldFire(GuardLevel.Soft, it * 1_000L) }
        assertEquals(9, fired) // at 0, 5, 10 ... 40 s
    }

    @Test fun aHigherLevelSoundsAtOnce() {
        val p = AlertPolicy()
        assertTrue(p.shouldFire(GuardLevel.Soft, 0))
        assertTrue(p.shouldFire(GuardLevel.Firm, 1_000))
        assertTrue(p.shouldFire(GuardLevel.Spoken, 2_000))
    }

    @Test fun hoveringAtTheLimitDoesNotSoundAgainAndAgain() {
        val p = AlertPolicy()
        assertTrue(p.shouldFire(GuardLevel.Soft, 0))
        assertFalse(p.shouldFire(GuardLevel.Off, 1_000))
        assertFalse(p.shouldFire(GuardLevel.Soft, 2_000)) // back over 2 s later: same alert
        assertFalse(p.shouldFire(GuardLevel.Off, 3_000))
        assertTrue(p.shouldFire(GuardLevel.Soft, 6_000)) // a new crossing after the repeat gap
    }

    @Test fun neverFiresWhileOff() {
        val p = AlertPolicy()
        assertEquals(0, (0..100).count { p.shouldFire(GuardLevel.Off, it * 1_000L) })
    }

    @Test fun resetStartsFresh() {
        val p = AlertPolicy()
        assertTrue(p.shouldFire(GuardLevel.Firm, 0))
        p.reset()
        assertTrue(p.shouldFire(GuardLevel.Soft, 500))
    }
}
