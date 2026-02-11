package com.shadowai.app.ui.audit

import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingActionRegistryTest {

    @Test
    fun `registry defines user-facing actions`() {
        assertTrue(UserFacingActionRegistry.actions.isNotEmpty())
    }

    @Test
    fun `action ids are unique`() {
        val ids = UserFacingActionRegistry.actions.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }
}
