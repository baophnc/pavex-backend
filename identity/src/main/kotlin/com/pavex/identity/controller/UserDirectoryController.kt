package com.pavex.identity.controller

import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class UserNamesRequest(@field:Size(max = 200) val ids: List<UUID>)

/** What staff screens show instead of an id: the person's name and photo, nothing else. */
data class UserNameResponse(val id: UUID, val name: String, val avatarUrl: String?)

/**
 * Names behind user ids for staff screens (who scanned, who approved, who is assigned). Any staff
 * member may read them; emails and phone numbers stay behind identity.users.read.
 */
@RestController
@RequestMapping("/api/v1/users/names")
class UserDirectoryController(
    private val accounts: UserAccountRepository,
    private val profiles: UserProfileRepository,
) {
    @PostMapping
    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyAuthority('identity.users.read','scope.all-hubs','operations.hub.read','assignments.read-own')",
    )
    fun names(@Valid @RequestBody request: UserNamesRequest): List<UserNameResponse> {
        val ids = request.ids.toSet()
        if (ids.isEmpty()) return emptyList()
        val profileByUser = profiles.findAllByUserIdIn(ids).associateBy { it.userId }
        return accounts.findAllById(ids).map { account ->
            val profile = profileByUser[account.id]
            val fullName = listOfNotNull(profile?.lastName, profile?.firstName).joinToString(" ").trim()
            UserNameResponse(
                id = account.id,
                // A deleted account keeps its id under a placeholder email; show who it was.
                name = fullName.ifEmpty { (account.deletedEmail ?: account.email).substringBefore('@') },
                avatarUrl = profile?.avatarUrl,
            )
        }
    }
}
