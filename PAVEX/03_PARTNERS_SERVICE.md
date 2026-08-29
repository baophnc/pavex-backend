# Partners Service — Bản thuyết trình đầy đủ

## 1. Giới thiệu tổng quan

Ở **Partners Service**, chức năng chính là quản lý merchant và địa chỉ.

Module này quản lý province, ward, user address, default address, merchant profile, merchant status, merchant status history, pickup address, default pickup address và order capability.

Partners còn có chức năng kiểm tra merchant eligibility thông qua Identity và đồng bộ role MERCHANT sang Identity bằng outbox.

Merchant là một business entity riêng, không chỉ là role MERCHANT.

Nói ngắn gọn:

> Partners Service quản lý toàn bộ lifecycle của merchant và dữ liệu địa chỉ liên quan.

---

## 2. Chức năng Province

Partners cung cấp danh sách tỉnh/thành.

Location catalog này được các service khác sử dụng để validate location code.

---

## 3. Chức năng Ward

Partners cung cấp danh sách phường/xã theo provinceCode và xem chi tiết ward theo code.

Network sử dụng dữ liệu này khi resolve location.

---

## 4. Chức năng User Address

User có thể tạo, xem, cập nhật, xóa và đặt một địa chỉ làm mặc định.

Service kiểm tra ownership để user chỉ thao tác trên address của mình.

---

## 5. Default User Address

Mỗi user chỉ có một địa chỉ mặc định trong cùng thời điểm.

Database có constraint hỗ trợ rule này.

---

## 6. Chức năng tạo Merchant Profile

User tạo merchant profile sau khi Partners kiểm tra eligibility từ Identity.

Merchant mới thường vào trạng thái PENDING_VERIFICATION.

---

## 7. Merchant Eligibility

Partners hỏi Identity user có ACTIVE, email verified và profile COMPLETED hay không.

Nếu thiếu điều kiện thì merchant flow có thể bị từ chối.

---

## 8. Merchant Status

Các trạng thái chính gồm:

```text
PENDING_VERIFICATION
ACTIVE
SUSPENDED
REJECTED
```

---

## 9. Chức năng Submit Verification

User submit merchant profile để admin xét duyệt.

---

## 10. Chức năng Resubmit

Nếu merchant bị REJECTED, user có thể sửa thông tin rồi submit lại.

---

## 11. Chức năng Admin Approve

Admin approve merchant để chuyển sang ACTIVE.

Sau khi status đổi, Partners ghi history và outbox để đồng bộ role MERCHANT.

---

## 12. Chức năng Reject

Admin có thể reject merchant và lưu reason.

Merchant có thể sửa rồi resubmit sau.

---

## 13. Chức năng Suspend

Merchant ACTIVE có thể bị chuyển sang SUSPENDED.

Khi không còn active, Partners có thể đồng bộ tắt role MERCHANT.

---

## 14. Chức năng Reactivate

Merchant SUSPENDED có thể được chuyển lại ACTIVE theo rule.

---

## 15. Merchant Status History

Mỗi lần status đổi, hệ thống lưu:

```text
fromStatus
toStatus
actor
reason
time
```

Dùng để audit.

---

## 16. Chức năng Pickup Address

Merchant có thể tạo, sửa, xóa và đặt pickup address mặc định.

Default pickup được dùng khi tạo order.

---

## 17. Merchant Role Outbox

Partners không phụ thuộc vào việc Identity phải trả lời thành công ngay trong merchant transaction.

Thay vào đó, Partners ghi outbox rồi processor gọi Identity sau.

Nếu lỗi thì retry.

---

## 18. Chức năng Order Capability

Shipment hỏi Partners merchant có được tạo order hay không.

Partners kiểm tra:

```text
Merchant ACTIVE
Identity eligible
Có default pickup address
```

Nếu đủ thì `canCreateOrder=true`.

---

## 19. Vì sao không chỉ nhìn Role MERCHANT?

Role chỉ thể hiện authorization.

Merchant còn có status business và pickup requirement.

Một user có role MERCHANT nhưng merchant bị SUSPENDED vẫn không được tạo order.

---

## 20. Database chính

Các bảng quan trọng gồm `provinces`, `wards`, `user_addresses`, `merchants`, `merchant_status_history`, `merchant_pickup_addresses` và `merchant_role_outbox`.

---

## 21. Câu trả lời hội đồng

**Tại sao Merchant không nằm Identity?**  
Vì Merchant có lifecycle và dữ liệu business riêng.

**Tại sao cần Outbox?**  
Để việc sync role sang Identity có thể retry nếu service tạm lỗi.

**Merchant ACTIVE có chắc tạo order được không?**  
Không. Còn cần Identity eligible và default pickup.

**Location catalog nằm đâu?**  
Partners.

---

## 22. File code quan trọng

Các file nên nhớ là `MerchantController.kt`, `AdminMerchantController.kt`, `MerchantPickupAddressController.kt`, `UserAddressController.kt`, `LocationController.kt`, `InternalMerchantController.kt`, `MerchantService.kt`, `MerchantPickupAddressService.kt`, `UserAddressService.kt`, `LocationService.kt`, `IdentityClient.kt`, `MerchantRoleOutboxProcessor.kt`, `ServiceJwtIssuer.kt` và `SecurityConfig.kt`.
