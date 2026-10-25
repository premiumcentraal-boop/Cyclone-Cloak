package dev.cyclone.cloak.data

import dev.cyclone.cloak.root.CloakStateLayout
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloakSpaceStoreTest {
    private val id = "Cloak_0123456789abcdef"

    private fun space(state: String = CloakSpace.STATE_READY, identity: String? = "identity-1", user: Int = 10) =
        CloakSpace(id, "Work", user, listOf("com.example.app"), identity, state, 1L, 2L)

    @Test
    fun roundTripKeepsEveryField() {
        val saved = listOf(
            space(),
            space(identity = null, state = CloakSpace.STATE_CREATING).copy(id = "Cloak_fedcba9876543210"),
        )
        assertEquals(saved, CloakSpaceStore.parse(CloakSpaceStore.serialize(saved)))
    }

    @Test
    fun onlyCloakIdsAreSpaces() {
        assertTrue(CloakSpace.isSpaceId(id))
        assertFalse(CloakSpace.isSpaceId("Cyclone_0123456789abcdef"))
        assertFalse(CloakSpace.isSpaceId("Cloak_0123456789ABCDEF"))
    }

    @Test
    fun newIdsAreSpaceIds() {
        repeat(20) { assertTrue(CloakSpace.isSpaceId(CloakSpace.newId())) }
    }

    @Test
    fun invalidRecordsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { space().copy(id = "Cyclone_0123456789abcdef") }
        assertThrows(IllegalArgumentException::class.java) { space().copy(label = "  ") }
        assertThrows(IllegalArgumentException::class.java) { space().copy(androidUserId = -1) }
    }

    @Test
    fun anUnreadableFileIsAnErrorNotAnEmptyList() {
        assertThrows(JSONException::class.java) { CloakSpaceStore.parse("not json") }
    }

    @Test
    fun upsertReplacesTheSameSpaceAndRemoveForgetsIt() {
        val creating = space(state = CloakSpace.STATE_CREATING)
        val ready = creating.copy(state = CloakSpace.STATE_READY)
        assertEquals(listOf(ready), CloakSpaceStore.upsertIn(listOf(creating), ready))
        assertTrue(CloakSpaceStore.removeIn(listOf(ready), id).isEmpty())
    }

    @Test
    fun bindingsAndTheStateLayoutAcceptSpaceIdsAndStillAcceptCyclone() {
        CloakBindingStore.validate(id, 10, "com.example.app")
        CloakBindingStore.validate("Cyclone_0123456789abcdef", 11, "com.example.app")
        CloakStateLayout.validateInputs(id, 10, "com.example.app")
        CloakStateLayout.validateInputs("Cyclone_0123456789abcdef", 11, "com.example.app")
        assertThrows(IllegalArgumentException::class.java) { CloakBindingStore.validate("Other_0123456789abcdef", 10, "com.example.app") }
    }
}
