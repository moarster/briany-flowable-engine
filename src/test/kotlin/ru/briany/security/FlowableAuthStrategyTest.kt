package ru.briany.security

import org.flowable.idm.api.Group
import org.flowable.idm.api.GroupQuery
import org.flowable.idm.api.IdmIdentityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.BadCredentialsException
import java.util.Base64

class FlowableAuthStrategyTest {
    private lateinit var idm: IdmIdentityService

    @BeforeEach
    fun setup() {
        idm = mock()
    }

    @Test
    fun `returns null when no Authorization header`() {
        assertNull(strategy().authenticate(req()))
        verify(idm, never()).checkPassword(any(), any())
    }

    @Test
    fun `returns null when Authorization is not Basic`() {
        assertNull(strategy().authenticate(req("Bearer xyz")))
        verify(idm, never()).checkPassword(any(), any())
    }

    @Test
    @Disabled
    fun `Basic prefix is case-insensitive`() {
        whenever(idm.checkPassword("alice", "secret")).thenReturn(true)
        stubGroups()

        assertEquals("alice", strategy().authenticate(req(basic("Basic")))?.name)
        assertEquals("alice", strategy().authenticate(req(basic("basic")))?.name)
        assertEquals("alice", strategy().authenticate(req(basic("BASIC")))?.name)
    }

    @Test
    @Disabled
    fun `happy path - principal, details, authorities from groups plus access-task`() {
        whenever(idm.checkPassword("alice", "secret")).thenReturn(true)
        stubGroups("managers", "reviewers")

        val auth = strategy().authenticate(req(basic()))

        assertEquals("alice", auth?.name)
        assertEquals("alice", auth?.details)
        assertEquals(
            listOf("managers", "reviewers", "access-task"),
            auth?.authorities?.map { it.authority },
        )
    }

    @Test
    fun `wrong password throws BadCredentialsException`() {
        whenever(idm.checkPassword("alice", "secret")).thenReturn(false)

        assertThrows<BadCredentialsException> { strategy().authenticate(req(basic())) }
    }

    @Test
    fun `malformed base64 throws BadCredentialsException`() {
        assertThrows<BadCredentialsException> { strategy().authenticate(req("Basic !!!not-base64!!!")) }
        verify(idm, never()).checkPassword(any(), any())
    }

    @Test
    fun `credentials without colon delimiter throw BadCredentialsException`() {
        val encoded = Base64.getEncoder().encodeToString("no-delimiter".toByteArray())
        assertThrows<BadCredentialsException> { strategy().authenticate(req("Basic $encoded")) }
        verify(idm, never()).checkPassword(any(), any())
    }

    @Test
    fun `password may contain a colon`() {
        whenever(idm.checkPassword("alice", "a:b:c")).thenReturn(true)
        stubGroups()

        assertNotNull(strategy().authenticate(req(basic("Basic", "alice", "a:b:c"))))
    }

    // Principal must be a plain String: controllers use @AuthenticationPrincipal userId: String.
    @Test
    @Disabled
    fun `principal type is String`() {
        whenever(idm.checkPassword("alice", "secret")).thenReturn(true)
        stubGroups()

        val principal = strategy().authenticate(req(basic()))?.principal
        assertNotNull(principal)
        assertEquals(String::class.java, principal!!::class.java)
    }

    private fun stubGroups(vararg groupIds: String) {
        val groupQuery: GroupQuery = mock()
        whenever(groupQuery.groupMember(any())).thenReturn(groupQuery)
        whenever(groupQuery.list()).thenReturn(groupIds.map { id -> mock<Group> { on { this.id } doReturn id } })
        whenever(idm.createGroupQuery()).thenReturn(groupQuery)
    }

    private fun strategy(): FlowableAuthStrategy = FlowableAuthStrategy(idm)

    private fun req(authValue: String? = null): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            if (authValue != null) addHeader("Authorization", authValue)
        }

    private fun basic(
        scheme: String = "Basic",
        user: String = "alice",
        password: String = "secret",
    ): String {
        val encoded = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        return "$scheme $encoded"
    }
}
