# Tổng quan Pavex Backend — Bản thuyết trình

## 1. Giới thiệu tổng quan

Hệ thống Pavex được chia thành nhiều microservice để mỗi service chỉ chịu trách nhiệm cho một nhóm nghiệp vụ riêng.

Ở mức tổng quan, **Auth Service** quản lý đăng ký, đăng nhập, mật khẩu, token và session. **Identity Service** quản lý user, profile, trạng thái account, role và permission. **Partners Service** quản lý merchant, địa chỉ và location catalog. **Network Service** quản lý mạng lưới vận chuyển như region, hub, service area, lane, schedule, route và capacity. **Pricing Service** quản lý bảng giá và tính quote.

Ngoài ra còn có các shared library dùng để chia sẻ contract và primitive kỹ thuật giữa các service.

Điểm quan trọng của kiến trúc này là các service **không đọc trực tiếp database của nhau**. Khi cần dữ liệu của service khác, hệ thống gọi API nội bộ và dùng service JWT để xác thực.

---

## 2. Auth Service quản lý gì?

Auth quản lý phần xác thực người dùng.

Các chức năng chính gồm đăng ký tài khoản, đăng nhập, access token, refresh token, refresh token rotation, logout, logout all, session, email verification, forgot password, reset password, change password, invitation và Google login.

Auth cũng có các cơ chế bảo mật như rate limit, login lockout, audit, outbox và RabbitMQ cho email.

Auth không quản lý role và permission. Khi cần quyền của user, Auth gọi Identity.

---

## 3. Identity Service quản lý gì?

Identity quản lý account, profile, role, permission, account status và authorization context.

Identity còn có chức năng quản lý user cho admin, gán và xóa role, bảo vệ admin cuối cùng, kiểm tra merchant eligibility và đồng bộ role MERCHANT từ Partners.

Khi account bị suspend hoặc disabled, Identity gọi Auth để revoke session.

---

## 4. Partners Service quản lý gì?

Partners quản lý province, ward, user address, merchant profile, merchant status, pickup address và order capability.

Khi user đăng ký merchant, Partners gọi Identity để kiểm tra user có đủ điều kiện không.

Khi merchant được approve hoặc không còn active, Partners dùng outbox để đồng bộ role MERCHANT sang Identity.

Shipment có thể gọi Partners để hỏi merchant có được tạo order hay không.

---

## 5. Network Service quản lý gì?

Network quản lý region, hub, service area, coverage, lane, schedule, route template, route planning và capacity.

Network còn cung cấp chức năng resolve location cho Pricing.

Pricing không tự biết địa chỉ thuộc Local, Regional hay National. Nó gọi Network để lấy Service Area, Region và Coverage Tier.

---

## 6. Pricing Service quản lý gì?

Pricing quản lý Rate Plan, Rate Rule, Standard, Express, Pricing Zone, Actual Weight, Volumetric Weight, Chargeable Weight, phụ phí Remote, Island, COD, Insurance, ETA và Quote Snapshot.

Pricing không tính route hoặc capacity. Network xử lý phần đó.

---

## 7. Cách các service giao tiếp

Khi user login:

```text
Client -> Auth -> Identity -> Auth -> Client
```

Auth kiểm tra credential, sau đó gọi Identity lấy role và permission rồi mới phát token.

Khi user đăng ký merchant:

```text
Client -> Partners -> Identity
```

Partners hỏi Identity user có đủ điều kiện hay không.

Khi Pricing tạo quote:

```text
Client -> Pricing -> Network
```

Pricing hỏi Network địa chỉ thuộc Service Area, Region và Coverage Tier nào.

---

## 8. Service JWT

Các internal API sử dụng service JWT.

Ví dụ Pricing gọi Network:

```text
sub   = pricing-service
aud   = network-service
scope = network.pricing.resolve
typ   = service
```

Mục tiêu là mỗi service chỉ được gọi đúng internal API mà nó cần.

---

## 9. Eureka

Eureka dùng để service tìm thấy nhau bằng tên.

Ví dụ:

```text
auth-service
identity-service
partners-service
network-service
pricing-service
```

Eureka chỉ làm service discovery, không thay thế authentication.

---

## 10. Cách nói ngắn trước hội đồng

> Pavex được chia thành nhiều microservice theo từng nghiệp vụ. Auth quản lý xác thực, Identity quản lý user và quyền, Partners quản lý merchant và địa chỉ, Network quản lý mạng lưới vận chuyển, còn Pricing quản lý bảng giá và quote. Các service giao tiếp qua API nội bộ và service JWT, thay vì đọc trực tiếp database của nhau.
