package ru.briany.security

import org.flowable.idm.api.IdmIdentityService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.briany.generated.api.IdentityApi
import ru.briany.generated.model.User

@RestController
class UserController(
    private val idmIdentityService: IdmIdentityService,
) : IdentityApi {
    override fun getCurrentUser(): ResponseEntity<User> {
        val userId =
            SecurityContextHolder.getContext().authentication?.name
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated principal")

        val idmUser = idmIdentityService.createUserQuery().userId(userId).singleResult()
        val groupIds =
            idmIdentityService
                .createGroupQuery()
                .groupMember(userId)
                .list()
                .map { it.id }

        return ResponseEntity.ok(
            User(
                id = userId,
                displayName = idmUser.displayName,
                groupIds = groupIds,
            ),
        )
    }
}
