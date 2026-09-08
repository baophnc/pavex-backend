package com.pavex.operations.service

import com.pavex.contracts.operations.ProvisionWorkforceIdentityRequest
import com.pavex.operations.client.IdentityWorkforceClient
import com.pavex.operations.client.NetworkHubClient
import com.pavex.operations.dto.AssignWorkforceHubRequest
import com.pavex.operations.dto.CreateWorkforceMemberRequest
import com.pavex.operations.dto.DispatcherAccessLevel
import com.pavex.operations.dto.OnboardWorkforceMemberRequest
import com.pavex.operations.dto.SetWorkforceCapacityRequest
import com.pavex.operations.dto.WorkforceMemberResponse
import com.pavex.operations.entity.WorkforceMemberKind
import com.pavex.operations.exception.InvalidOperationsRequestException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class WorkforceOnboardingService(
    private val identity: IdentityWorkforceClient,
    private val network: NetworkHubClient,
    private val workforce: WorkforceService,
) {
    @Transactional
    fun onboard(
        actorUserId: UUID,
        scope: WorkforceAccessScope,
        request: OnboardWorkforceMemberRequest,
    ): WorkforceMemberResponse {
        if (!scope.isGlobal) throw AccessDeniedException("Global workforce permission is required")
        validate(request)
        validateHub(request)
        val roleCode = workforceRole(request)
        val provisioned = identity.provision(
            ProvisionWorkforceIdentityRequest(
                actorUserId = actorUserId,
                email = request.email.trim().lowercase(),
                roleCode = roleCode,
                firstName = request.firstName.trim(),
                lastName = request.lastName.trim(),
                displayName = request.displayName?.trim()?.takeIf { it.isNotEmpty() },
                gender = request.gender.name,
                dateOfBirth = request.dateOfBirth,
                avatarUrl = request.avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )

        var member = workforce.create(
            actorUserId,
            scope,
            CreateWorkforceMemberRequest(provisioned.userId, request.kind),
        )
        request.hubId?.let { hubId ->
            member = workforce.assignHub(
                actorUserId,
                scope,
                member.id,
                AssignWorkforceHubRequest(hubId = hubId, isPrimary = true, reason = "Initial workforce onboarding"),
            )
        }
        request.maxConcurrentAssignments?.let { capacity ->
            member = workforce.setCapacity(
                actorUserId,
                scope,
                member.id,
                SetWorkforceCapacityRequest(capacity, "Initial workforce onboarding"),
            )
        }
        return member
    }

    private fun validate(request: OnboardWorkforceMemberRequest) {
        if (request.email.isBlank() || '@' !in request.email) {
            throw InvalidOperationsRequestException("invalid_workforce_email", "A valid workforce email is required.")
        }
        if (request.firstName.isBlank() || request.lastName.isBlank()) {
            throw InvalidOperationsRequestException("incomplete_workforce_profile", "First name and last name are required for workforce onboarding.")
        }
        if (request.dateOfBirth.isAfter(LocalDate.now())) {
            throw InvalidOperationsRequestException("invalid_workforce_birth_date", "Date of birth cannot be in the future.")
        }
        if (request.maxConcurrentAssignments != null && request.maxConcurrentAssignments !in 1..100) {
            throw InvalidOperationsRequestException("invalid_capacity", "Maximum concurrent assignments must be between 1 and 100.")
        }
        val hubRequired = request.kind != WorkforceMemberKind.DISPATCHER || request.dispatcherAccessLevel != DispatcherAccessLevel.GLOBAL
        if (hubRequired && request.hubId == null) {
            throw InvalidOperationsRequestException("workforce_hub_required", "A primary hub is required for this workforce role.")
        }
        if (request.kind == WorkforceMemberKind.DISPATCHER && request.dispatcherAccessLevel == null) {
            throw InvalidOperationsRequestException(
                "dispatcher_access_required",
                "Choose HUB, HUB_MANAGER or GLOBAL dispatcher access.",
            )
        }
    }

    private fun validateHub(request: OnboardWorkforceMemberRequest) {
        val hubId = request.hubId ?: return
        val hub = network.find(hubId)
            ?: throw InvalidOperationsRequestException("workforce_hub_not_found", "The selected workforce hub does not exist.")
        if (hub.status != "ACTIVE") {
            throw InvalidOperationsRequestException("workforce_hub_inactive", "The selected workforce hub is not active.")
        }
    }

    private fun workforceRole(request: OnboardWorkforceMemberRequest): String {
        if (request.kind == WorkforceMemberKind.DISPATCHER && request.dispatcherAccessLevel == null) {
            throw InvalidOperationsRequestException(
                "dispatcher_access_required",
                "Choose HUB, HUB_MANAGER or GLOBAL dispatcher access.",
            )
        }
        return WorkforceRolePolicy.onboardingRole(request.kind, request.dispatcherAccessLevel)
    }
}
