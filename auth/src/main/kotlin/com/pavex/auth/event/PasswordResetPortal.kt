package com.pavex.auth.event

// Only a named, server-configured portal is accepted, never an arbitrary URL.
enum class PasswordResetPortal { CLIENT, MANAGEMENT }
