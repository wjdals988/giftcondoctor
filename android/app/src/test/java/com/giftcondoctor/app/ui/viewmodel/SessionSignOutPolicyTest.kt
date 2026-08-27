package com.giftcondoctor.app.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SessionSignOutPolicyTest {
    @Test
    fun `token cleanup succeeds before auth sign out`() = runTest {
        val events = mutableListOf<String>()

        val result = performSafeSignOut(
            deletePushToken = { events += "deleteToken" },
            signOutAuth = { events += "signOut" }
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("deleteToken", "signOut"), events)
    }

    @Test
    fun `token cleanup failure keeps auth session`() = runTest {
        var signedOut = false

        val result = performSafeSignOut(
            deletePushToken = { error("offline") },
            signOutAuth = { signedOut = true }
        )

        assertTrue(result.isFailure)
        assertFalse(signedOut)
    }

    @Test
    fun `cancellation propagates without auth sign out`() = runTest {
        var signedOut = false

        try {
            performSafeSignOut(
                deletePushToken = { throw CancellationException("cancelled") },
                signOutAuth = { signedOut = true }
            )
            fail("CancellationException should propagate")
        } catch (_: CancellationException) {
            assertFalse(signedOut)
        }
    }
}
