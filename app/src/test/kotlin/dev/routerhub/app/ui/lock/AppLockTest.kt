package dev.routerhub.app.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 0L
    private var enabled = true
    private fun lock() = AppLock({ enabled }, { now })

    @Test
    fun `the app starts locked while the lock is on`() {
        assertTrue(lock().locked.value)
        enabled = false
        assertFalse(lock().locked.value)
    }

    @Test
    fun `a short trip away doesn't lock, a long one does`() {
        val lock = lock().apply { unlock() }
        lock.onStop()
        now += AppLock.GRACE_MILLIS
        lock.onStart()
        assertFalse(lock.locked.value)

        lock.onStop()
        now += AppLock.GRACE_MILLIS + 1
        lock.onStart()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `nothing locks while the lock is off, and turning it on doesn't lock at once`() {
        enabled = false
        val lock = lock()
        lock.onStop()
        now += 10 * AppLock.GRACE_MILLIS
        lock.onStart()
        assertFalse(lock.locked.value)
        enabled = true
        assertFalse(lock.locked.value)
    }

    @Test
    fun `coming back without having left, as after rotating, doesn't lock`() {
        val lock = lock().apply { unlock() }
        now += 10 * AppLock.GRACE_MILLIS
        lock.onStart()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `coming back from a slow screen lock entry doesn't lock again`() {
        val lock = lock()
        lock.onStop() // the screen lock covers the app
        now += 2 * AppLock.GRACE_MILLIS
        lock.unlock()
        lock.onStart()
        assertFalse(lock.locked.value)
    }
}
