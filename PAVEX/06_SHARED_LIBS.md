# Shared Libraries — Bản thuyết trình đầy đủ

## 1. Giới thiệu tổng quan

Shared Libraries không phải microservice chạy độc lập.

Trong hệ thống hiện tại có hai module chính là `platform-contracts` và `platform-common`.

`platform-contracts` dùng để định nghĩa dữ liệu giao tiếp giữa các service.

`platform-common` dùng để chứa primitive kỹ thuật dùng chung.

Nói ngắn gọn:

> Shared Libraries giúp các service dùng chung contract và constant mà không cần share business entity hoặc database model.

---

## 2. platform-contracts

Module này chứa DTO thật sự đi qua boundary giữa microservice.

Ví dụ Auth và Identity dùng authorization context contract.

Partners và Identity dùng merchant eligibility và merchant role sync contract.

Pricing và Network dùng location resolution contract.

Pricing và Shipment dùng quote snapshot contract.

---

## 3. Vì sao cần Contract?

Nếu Pricing import trực tiếp Network entity thì Pricing sẽ phụ thuộc chặt vào implementation của Network.

Dùng contract giúp Network giữ entity và database model riêng.

Pricing chỉ biết đúng các field cần dùng.

---

## 4. platform-common

Module này chứa các primitive kỹ thuật như:

```text
PageResponse
PavexHeaders
PavexJwtClaims
PavexTokenTypes
```

---

## 5. Không nên đưa gì vào Shared Libs?

Không nên đưa JPA Entity, Repository hoặc business service vào shared libs.

Các domain như Merchant, RatePlan, Hub hay UserAccount phải thuộc service sở hữu dữ liệu đó.

---

## 6. Breaking Change

Nếu contract thay đổi theo hướng breaking, nên version contract thay vì thay đổi âm thầm.

---

## 7. Câu trả lời hội đồng

**platform-common khác platform-contracts?**  
Common chứa primitive kỹ thuật, Contracts chứa DTO giao tiếp giữa service.

**Có nên share JPA Entity không?**  
Không. Entity là implementation riêng của service owner.

**Quote Snapshot nằm đâu?**  
platform-contracts.

**PageResponse nằm đâu?**  
platform-common.
