# Identity Service — Bản thuyết trình đầy đủ

## 1. Giới thiệu tổng quan

Ở **Identity Service**, chức năng chính là quản lý thông tin người dùng và quyền của họ trong hệ thống.

Module này quản lý account, profile, account status, profile status, role, permission, user-role mapping và role-permission mapping.

Ngoài ra Identity còn cung cấp authorization context cho Auth, quản lý user cho admin, kiểm tra merchant eligibility cho Partners, đồng bộ role MERCHANT và bảo vệ admin cuối cùng.

Identity không quản lý password, login, refresh token hay session. Những phần đó thuộc Auth.

Nói ngắn gọn:

> Identity Service quản lý user, profile và authorization của toàn hệ thống.

---

## 2. Chức năng lấy thông tin User hiện tại

Các endpoint `/me` lấy userId trực tiếp từ JWT subject.

User không truyền userId nên không thể dùng endpoint này để xem profile của người khác.

---

## 3. Chức năng cập nhật Profile

User có thể cập nhật first name, last name, display name, gender, date of birth và avatar tùy endpoint.

Khi profile đủ các trường bắt buộc thì `profileStatus` có thể chuyển sang COMPLETED.

---

## 4. Account Status

Identity quản lý các trạng thái:

```text
ACTIVE
SUSPENDED
DISABLED
```

ACTIVE là account đang hoạt động bình thường.

SUSPENDED là khóa tạm thời.

DISABLED là vô hiệu hóa.

---

## 5. Profile Status

Profile có các trạng thái như:

```text
INCOMPLETE
COMPLETED
PENDING_VERIFICATION
REJECTED
```

Trong flow hiện tại, INCOMPLETE và COMPLETED là hai trạng thái thường gặp nhất.

---

## 6. Chức năng tìm kiếm User cho Admin

Admin có thể tìm user theo email hoặc tên.

Có thể lọc thêm theo role, account status hoặc profile status.

Kết quả có phân trang.

---

## 7. Chức năng xem chi tiết User

Admin có thể xem account, profile, role, permission và các thông tin liên quan của một user.

---

## 8. Chức năng tạo User

Admin có thể tạo user trong Identity.

Identity tạo account, profile và role, sau đó gọi Auth để provision invitation.

Identity không tạo password.

---

## 9. Chức năng Invite User

Identity gọi Auth để gửi invitation cho user đã được tạo.

---

## 10. Chức năng thay đổi Account Status

Admin có thể đổi trạng thái account.

Khi user rời ACTIVE, Identity tăng authorization version và gọi Auth revoke session.

---

## 11. Chức năng Role

Các role chính gồm:

```text
CUSTOMER
MERCHANT
DRIVER
WAREHOUSE_STAFF
HUB_MANAGER
OPERATIONS_MANAGER
ADMIN
```

Một user có thể có nhiều role.

---

## 12. Chức năng Permission

Permission là quyền cụ thể.

Ví dụ:

```text
identity.users.read
identity.users.create
identity.users.invite
identity.users.manage-status
identity.roles.assign
```

Controller có thể kiểm tra permission thay vì chỉ hard-code role.

---

## 13. Chức năng Assign Role

Admin có thể gán role cho user.

Nếu role đã có rồi thì không tạo duplicate.

Sau khi role thay đổi, authorization version tăng.

---

## 14. Chức năng Remove Role

Admin có thể xóa role nhưng có rule bảo vệ ADMIN.

Admin không thể tự remove ADMIN của chính mình.

Hệ thống cũng không cho remove ADMIN của admin active cuối cùng.

---

## 15. Bảo vệ Last Active Admin

Identity dùng pessimistic lock khi kiểm tra admin cuối cùng.

Mục tiêu là tránh hai request chạy cùng lúc khiến hệ thống mất toàn bộ admin.

---

## 16. Authorization Context

Auth gọi Identity để lấy:

```text
status
active
emailVerified
roles
permissions
authorizationVersion
```

Auth dùng dữ liệu này để tạo JWT.

---

## 17. authorizationVersion

authorizationVersion tăng khi authorization state thay đổi.

Ví dụ khi role đổi, status đổi hoặc email verification đổi.

Nó khác với JPA `version`.

`version` dùng cho optimistic locking, còn `authorizationVersion` mang ý nghĩa security/business.

---

## 18. Merchant Eligibility

Partners gọi Identity để hỏi user có đủ điều kiện làm merchant hay không.

Điều kiện hiện tại là:

```text
Account ACTIVE
Email verified
Profile COMPLETED
```

---

## 19. Đồng bộ Role MERCHANT

Partners gọi Identity với desired state.

Nếu enabled=true thì gán MERCHANT.

Nếu enabled=false thì xóa MERCHANT.

Flow này được thiết kế theo hướng idempotent.

---

## 20. Gọi Auth Revoke Session

Khi account bị suspend hoặc disabled, Identity gọi Auth để revoke session.

Nhờ đó account status ảnh hưởng trực tiếp tới khả năng tiếp tục sử dụng refresh session.

---

## 21. Database chính

Các bảng quan trọng gồm `user_account`, `user_profile`, `role`, `permission`, `user_role` và `role_permission`.

---

## 22. Câu trả lời hội đồng

**Role khác Permission?**  
Role là nhóm quyền, Permission là quyền cụ thể.

**Tại sao cần last active admin protection?**  
Để hệ thống không mất toàn bộ admin.

**`version` khác `authorizationVersion`?**  
`version` dùng cho optimistic locking, còn `authorizationVersion` dùng để theo dõi thay đổi quyền.

**Merchant eligibility nằm ở đâu?**  
Identity vì điều kiện nằm ở account và profile.

**Password nằm đâu?**  
Auth.

---

## 23. File code quan trọng

Các file nên nhớ là `AdminUserController.kt`, `IdentityController.kt`, `UserController.kt`, `RoleController.kt`, `UserRoleController.kt`, `InternalIdentityController.kt`, `AdminUserService.kt`, `UserService.kt`, `IdentityProfileService.kt`, `RoleService.kt`, `AuthCredentialClient.kt`, `AuthorizationPermissions.kt`, `SecurityConfig.kt` và `ServiceJwtIssuer.kt`.
