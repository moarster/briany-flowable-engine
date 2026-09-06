package ru.briany.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Maps Keycloak JWT claims to [GrantedAuthority].
 *
 * Roles come from `realm_access.roles` (Keycloak's `{"realm_access": {"roles": [...]}}`
 * shape), kept without a `ROLE_` prefix so Keycloak's naming matches
 * [org.springframework.security.core.Authentication] directly. `access-task` is always
 * added, the permission needed to enter the Flowable Workflow App (parity with
 * [FlowableAuthStrategy]).
 */
@Component
class KeycloakJwtAuthoritiesMapper {
    fun map(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccess = jwt.getClaimAsMap("realm_access")

        @Suppress("UNCHECKED_CAST")
        val roles = (realmAccess?.get("roles") as? List<String>) ?: emptyList()
        return (roles + ACCESS_TASK_AUTHORITY).map { SimpleGrantedAuthority(it) }
    }

    companion object {
        private const val ACCESS_TASK_AUTHORITY = "access-task"
    }
}
