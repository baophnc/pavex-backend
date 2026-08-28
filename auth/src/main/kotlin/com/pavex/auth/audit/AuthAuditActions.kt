package com.pavex.auth.audit

object AuthAuditActions {
    const val REGISTER = "auth.register"
    const val LOGIN = "auth.login"
    const val REFRESH = "auth.refresh"
    const val LOGOUT = "auth.logout"
    const val EMAIL_VERIFY = "auth.email.verify"
    const val EMAIL_RESEND = "auth.email.resend"
    const val PASSWORD_FORGOT = "auth.password.forgot"
    const val PASSWORD_RESET = "auth.password.reset"
    const val PASSWORD_CHANGE = "auth.password.change"
    const val SESSION_REVOKE = "auth.session.revoke"
    const val LOGOUT_ALL = "auth.logout.all"
    const val INVITATION_ACCEPT = "auth.invitation.accept"
    const val GOOGLE_LOGIN = "auth.google.login"
}
