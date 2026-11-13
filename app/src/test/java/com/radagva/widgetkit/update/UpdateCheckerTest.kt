package com.radagva.widgetkit.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun newerVersionsAreDetected() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0")) // numeric, not lexicographic
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.0"))
    }

    @Test
    fun sameOrOlderVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "1.0.0"))
    }

    @Test
    fun malformedReleaseTagsAreIgnored() {
        assertFalse(UpdateChecker.isNewer("latest", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0-beta", "1.0.0"))
    }
}
