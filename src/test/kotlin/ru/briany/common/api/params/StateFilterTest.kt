package ru.briany.common.api.params

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.briany.generated.model.ProcessInstanceState

class StateFilterTest {
    @Test
    fun `null returns All`() {
        assertSame(StateFilter.All, StateFilter.parse(null))
    }

    @Test
    fun `running parses to RUNNING`() {
        val result = StateFilter.parse("running")
        assertEquals(StateFilter.Of(ProcessInstanceState.RUNNING), result)
    }

    @Test
    fun `completed parses to COMPLETED`() {
        val result = StateFilter.parse("completed")
        assertEquals(StateFilter.Of(ProcessInstanceState.COMPLETED), result)
    }

    @Test
    fun `suspended parses to SUSPENDED`() {
        val result = StateFilter.parse("suspended")
        assertEquals(StateFilter.Of(ProcessInstanceState.SUSPENDED), result)
    }

    @Test
    fun `invalid value throws`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                StateFilter.parse("unknown")
            }
        assertEquals(true, ex.message?.contains("unknown"))
    }

    @Test
    fun `values are case sensitive`() {
        assertThrows<IllegalArgumentException> {
            StateFilter.parse("Running")
        }
    }
}
