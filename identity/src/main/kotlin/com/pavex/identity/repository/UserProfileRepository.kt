package com.pavex.identity.repository

import com.pavex.identity.entity.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository :
    JpaRepository<UserProfile, UUID>    {
    fun findAllByUserIdIn(
        userIds: Collection<UUID>,
    ): List<UserProfile>
    }


