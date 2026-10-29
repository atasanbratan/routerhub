package dev.routerhub.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun `a higher number in any part is newer`() {
        assertTrue(AppUpdateChecker.isNewer("0.1.1", "0.1.0"))
        assertTrue(AppUpdateChecker.isNewer("0.2.0", "0.1.9"))
        assertTrue(AppUpdateChecker.isNewer("1.0.0", "0.99.99"))
    }

    @Test
    fun `parts are compared as numbers, not text`() {
        assertTrue(AppUpdateChecker.isNewer("0.10.0", "0.9.1"))
        assertFalse(AppUpdateChecker.isNewer("0.9.1", "0.10.0"))
    }

    @Test
    fun `the same or an older version is not an update`() {
        assertFalse(AppUpdateChecker.isNewer("0.1.0", "0.1.0"))
        assertFalse(AppUpdateChecker.isNewer("0.1", "0.1.0"))
        assertFalse(AppUpdateChecker.isNewer("0.0.9", "0.1.0"))
    }

    @Test
    fun `a v prefix and a build suffix are ignored`() {
        assertFalse(AppUpdateChecker.isNewer("v0.1.0", "0.1.0-debug"))
        assertTrue(AppUpdateChecker.isNewer("v0.1.1", "0.1.0-debug"))
    }
}
