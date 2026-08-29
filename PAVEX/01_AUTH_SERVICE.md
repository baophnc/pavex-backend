# Auth Service — Bản thuyết trình đầy đủ

## 1. Giới thiệu tổng quan

Ở **Auth Service**, chức năng chính là quản lý toàn bộ quá trình xác thực người dùng.

Module này xử lý đăng ký, đăng nhập, access token, refresh token, refresh token rotation, logout, quản lý session, email verification, quên mật khẩu, đặt lại mật khẩu, đổi mật khẩu, invitation và Google login.

Ngoài các chức năng chính, Auth còn có rate limit, login lockout, audit log, outbox, RabbitMQ và cơ chế mã hóa payload nhạy cảm trong outbox.

Auth không quản lý profile, role hay permission. Những dữ liệu đó thuộc Identity Service. Khi Auth cần biết một user có quyền gì thì Auth sẽ gọi Identity để lấy authorization context.

Nói ngắn gọn:

> Auth Service chịu trách nhiệm xác thực người dùng và quản lý vòng đời credential, token và session.

---

## 2. Chức năng đăng ký

Khi user đăng ký bằng email và password, Auth chuẩn hóa email, kiểm tra email đã tồn tại chưa, hash password rồi tạo account.

Sau đó Auth tạo email verification token và đưa yêu cầu gửi email vào outbox.

Password không được lưu plaintext.

---

## 3. Chức năng đăng nhập

Khi user login, Auth kiểm tra account, lockout, password và email verification.

Nếu credential hợp lệ, Auth gọi Identity để lấy account status, role, permission và authorization version.

Chỉ khi Identity xác nhận user đang ACTIVE thì Auth mới tạo access token và refresh session.

---

## 4. Chức năng Access Token

Access token là JWT dùng cho các request đã đăng nhập.

Token có thể chứa:

```text
userId
roles
permissions
authorizationVersion
```

Access token demo có thời gian sống ngắn, khoảng 15 phút.

---

## 5. Chức năng Refresh Token

Refresh token dùng để xin access token mới mà không cần login lại.

Raw refresh token không lưu trực tiếp trong database. Auth chỉ lưu SHA-256 hash.

Refresh token demo có thời gian sống khoảng 30 ngày.

---

## 6. Refresh Token Rotation

Mỗi lần refresh thành công, token cũ bị revoke và Auth tạo refresh token mới.

Cách này giúp một token không được sử dụng mãi mãi.

---

## 7. Refresh Token Reuse Detection

Nếu token đã rotate mà lại được sử dụng tiếp thì hệ thống xem đây là dấu hiệu token có thể bị đánh cắp.

Trong trường hợp đó, Auth có thể revoke toàn bộ active refresh session của user.

---

## 8. Chức năng Logout

Logout revoke refresh session tương ứng với token hiện tại.

Sau khi logout, refresh token đó không thể tiếp tục dùng để xin access token mới.

---

## 9. Chức năng Logout All

Logout All revoke toàn bộ refresh session của user.

Chức năng này phù hợp khi user muốn đăng xuất tất cả thiết bị hoặc khi có nghi ngờ credential bị lộ.

---

## 10. Chức năng quản lý Session

Auth lưu refresh session để biết user đang có những phiên đăng nhập nào.

User có thể xem session, revoke một session hoặc logout toàn bộ.

Session có thể chứa thông tin như IP, User-Agent, thời gian tạo và thời gian hết hạn.

---

## 11. Chức năng Email Verification

Auth tạo verification token, lưu hash token rồi gửi email qua outbox.

Khi user bấm link xác minh, Auth kiểm tra token, đánh dấu email verified và đồng bộ trạng thái sang Identity.

---

## 12. Chức năng Resend Verification

Nếu user chưa nhận được email xác minh, Auth có thể gửi lại.

Endpoint này có rate limit để tránh spam.

---

## 13. Chức năng Forgot Password

User nhập email để yêu cầu reset password.

Auth tạo reset token và gửi email.

Response không nên cho biết email đó có tồn tại hay không để tránh user enumeration.

---

## 14. Chức năng Reset Password

Auth nhận reset token và password mới.

Nếu token hợp lệ, Auth đổi password và revoke session để giảm nguy cơ token cũ tiếp tục được dùng.

---

## 15. Chức năng Change Password

User đã login có thể đổi password.

Auth kiểm tra current password trước rồi mới cho đặt password mới.

---

## 16. Chức năng Invitation

Khi admin tạo user ở Identity, Identity gọi Auth để provision credential và invitation.

User nhận lời mời rồi đặt password để hoàn tất tài khoản.

---

## 17. Chức năng Google Login

Auth verify Google ID token rồi map Google account với user Pavex thông qua external identity.

Nếu là user mới, Auth có thể tạo account và provision phần Identity tương ứng.

---

## 18. Chức năng lấy Authorization từ Identity

Auth không lưu role và permission như source of truth.

Khi cần phát access token, Auth gọi Identity để lấy status, role, permission và authorization version.

---

## 19. Chức năng Outbox

Các email như verify email, reset password và invitation được ghi vào outbox trước.

Sau khi database commit, publisher mới gửi message sang RabbitMQ.

Nếu RabbitMQ tạm lỗi thì outbox vẫn còn để retry.

---

## 20. Chức năng RabbitMQ Consumer

Consumer nhận message và gửi email tương ứng.

Hệ thống có consumed-message để giảm xử lý trùng một message nhiều lần.

---

## 21. Mã hóa Outbox Payload

Payload email có thể chứa token nhạy cảm.

Auth dùng AES-GCM để mã hóa payload trước khi lưu vào outbox.

---

## 22. Rate Limit và Lockout

Các endpoint nhạy cảm như login, register, refresh, forgot password và reset password đều có rate limit.

Nếu user nhập sai password nhiều lần, account có thể bị khóa tạm thời.

---

## 23. Internal API cho Identity

Identity gọi Auth để provision invitation, gửi invitation hoặc revoke session.

Request internal dùng service JWT.

Ví dụ:

```text
sub   = identity-service
aud   = auth-service
scope = auth.internal
typ   = service
```

---

## 24. Database chính

Các bảng quan trọng gồm `user_account`, `refresh_session`, `email_verification_token`, `password_reset_token`, `invitation_token`, `external_identity`, `outbox_message`, `consumed_message`, `auth_audit_log` và `auth_rate_limit`.

---

## 25. Câu trả lời hội đồng

**Tại sao Auth không giữ role?**  
Vì role và permission thuộc authorization domain của Identity.

**Tại sao refresh token lưu hash?**  
Để database bị lộ vẫn không có raw token để sử dụng trực tiếp.

**Tại sao cần refresh rotation?**  
Để token cũ không tiếp tục được dùng sau khi refresh thành công.

**Tại sao cần Outbox?**  
Để không mất email event khi RabbitMQ tạm lỗi.

**Tại sao forgot password không nói email có tồn tại?**  
Để chống user enumeration.

---

## 26. File code quan trọng

Các file nên nhớ khi demo là `AuthController.kt`, `AuthService.kt`, `RefreshTokenService.kt`, `SessionService.kt`, `EmailVerificationService.kt`, `PasswordResetService.kt`, `ChangePasswordService.kt`, `InvitationService.kt`, `GoogleAuthService.kt`, `JwtService.kt`, `IdentityClient.kt`, `OutboxService.kt`, `OutboxBatchPublisher.kt`, `AuthEmailConsumer.kt`, `OutboxPayloadCipher.kt` và `SecurityConfig.kt`.
