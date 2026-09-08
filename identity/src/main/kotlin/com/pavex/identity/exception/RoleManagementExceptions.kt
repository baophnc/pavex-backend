package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class RoleAlreadyExistsException(
    code: String,
) : ApiException(
    status = HttpStatus.CONFLICT,
    code = "role_already_exists",
    message = "Role '$code' already exists.",
)

class SystemRoleProtectedException : ApiException(
    status = HttpStatus.CONFLICT,
    code = "system_role_protected",
    message = "System roles cannot be modified or deleted.",
)

class RoleInUseException : ApiException(
    status = HttpStatus.CONFLICT,
    code = "role_in_use",
    message = "Role is assigned to one or more users and cannot be deleted.",
)

class WorkforceRoleManagedByOperationsException : ApiException(
    status = HttpStatus.CONFLICT,
    code = "workforce_role_managed_by_operations",
    message = "Operational workforce roles are managed by Operations > Workforce and cannot be assigned or removed directly in Identity.",
)

class RoleDelegationForbiddenException(
    message: String = "The requested role or permission set exceeds the actor's delegation authority.",
) : ApiException(
    status = HttpStatus.FORBIDDEN,
    code = "role_delegation_forbidden",
    message = message,
)

class SelfRoleMutationForbiddenException : ApiException(
    status = HttpStatus.FORBIDDEN,
    code = "self_role_mutation_forbidden",
    message = "Users cannot assign or remove their own roles.",
)

class UnknownPermissionException(
    permissionCodes: Collection<String>,
) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "unknown_permission",
    message = "Unknown permission code(s): ${permissionCodes.sorted().joinToString(", ")}",
)

class InvalidRoleCodeException : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "invalid_role_code",
    message = "Role code must start with A-Z and contain only A-Z, 0-9 or underscore (3-64 characters).",
)

class InvalidRoleNameException : ApiException(
    status = HttpStatus.BAD_REQUEST,
    code = "invalid_role_name",
    message = "Role name must contain between 2 and 128 non-whitespace characters.",
)
