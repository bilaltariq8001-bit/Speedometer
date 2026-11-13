package com.speedometer.app.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsContractTest {
    @Test
    fun knownEventsAreValid() {
        assertTrue(AnalyticsContract.validEvent("screen_enter"))
        assertTrue(AnalyticsContract.validEvent("trip_save_success"))
        assertTrue(AnalyticsContract.validEvent("ads_clicked"))
    }

    @Test
    fun unknownOrOverlongEventsAreRejected() {
        assertFalse(AnalyticsContract.validEvent("made_up_event"))
        assertFalse(AnalyticsContract.validEvent("screen_enter_" + "x".repeat(40)))
    }

    @Test
    fun everyEventNameFitsTheGa4Limit() {
        AnalyticsContract.events.forEach { name ->
            assertTrue("$name is longer than ${AnalyticsContract.MAX_EVENT_NAME}", name.length <= AnalyticsContract.MAX_EVENT_NAME)
            assertEquals("$name must be lowercase snake_case", name, name.lowercase())
        }
    }

    @Test
    fun userPropertiesAreCheckedForNameAndValueLength() {
        assertTrue(AnalyticsContract.validProperty("user_cohort", "first_time"))
        assertFalse(AnalyticsContract.validProperty("something_else", "x"))
        assertFalse(AnalyticsContract.validProperty("user_cohort", "x".repeat(AnalyticsContract.MAX_PROPERTY_VALUE + 1)))
    }
}
