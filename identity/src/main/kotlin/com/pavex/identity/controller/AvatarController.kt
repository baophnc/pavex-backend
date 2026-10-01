package com.pavex.identity.controller

import com.pavex.identity.dto.user.CurrentUserResponse
import com.pavex.identity.security.AuthorizationPermissions
import com.pavex.identity.service.AvatarService
import com.pavex.identity.service.UserService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

@RestController
@Tag(name = "Avatars", description = "Profile photo upload and public delivery")
class AvatarController(
    private val avatars: AvatarService,
    private val users: UserService,
) {
    /** The signed-in user replaces their own photo. */
    @PostMapping("/api/v1/auth/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadOwn(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestPart("file") file: MultipartFile,
    ): CurrentUserResponse {
        val userId = UUID.fromString(jwt.subject)
        avatars.replace(userId, file.contentType, file.bytes)
        return users.getCurrentUser(userId)
    }

    /** An administrator replaces someone's photo. */
    @PreAuthorize("hasAuthority('${AuthorizationPermissions.Identity.MANAGE_USER_PROFILE}')")
    @PostMapping("/api/v1/users/{userId}/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadFor(
        @PathVariable userId: UUID,
        @RequestPart("file") file: MultipartFile,
    ): AvatarUploadResponse = AvatarUploadResponse(avatars.replace(userId, file.contentType, file.bytes))

    /** Public: an avatar id is random and changes with every upload, so the image can be cached for good. */
    @GetMapping("/api/v1/avatars/{id}")
    fun read(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val image = avatars.read(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(image.bytes)
    }
}

data class AvatarUploadResponse(
    val avatarUrl: String,
)
