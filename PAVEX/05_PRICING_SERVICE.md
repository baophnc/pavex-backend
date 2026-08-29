# Pricing Service — Bản thuyết trình đầy đủ

## 1. Giới thiệu tổng quan

Ở **Pricing Service**, chức năng chính là quản lý toàn bộ phần **bảng giá và báo giá vận chuyển** của hệ thống.

Module này quản lý **Rate Plan**, tức là từng phiên bản bảng giá; quản lý **Rate Rule**, tức là mức giá cụ thể cho từng loại dịch vụ và từng vùng; đồng thời xử lý việc tạo **Quote** cho khách hàng.

Khi tính giá, Pricing có hai loại dịch vụ chính là **Standard** và **Express**. Về vùng tính giá thì có **Local, Regional và National**. Ngoài vùng giá, hệ thống còn có **Coverage Tier** gồm `STANDARD`, `REMOTE` và `ISLAND` để xác định có phụ phí khu vực hay không.

Pricing cũng quản lý phần tính khối lượng gồm **Actual Weight**, **Volumetric Weight** và **Chargeable Weight**. Chargeable Weight là khối lượng cuối cùng được dùng để tính phí vận chuyển.

Sau phần phí vận chuyển cơ bản, hệ thống còn có thể cộng **Additional Weight Fee**, **Remote Surcharge**, **Island Surcharge**, **COD Fee** và **Insurance Fee**.

Kết quả cuối cùng được lưu thành **Quote Snapshot**, trong đó có bảng giá đã sử dụng, revision, vùng giá, parcel, cân nặng, Standard, Express, các loại phí, ETA và thời gian hết hạn quote.

Pricing không tự quản lý Hub, Lane, Route hay Capacity. Những thông tin đó thuộc **Network Service**. Pricing chỉ gọi Network để biết địa chỉ gửi và nhận thuộc Service Area, Region và Coverage Tier nào.

Nói ngắn gọn:

> Pricing Service quản lý bảng giá và tạo báo giá. Nó xác định vùng vận chuyển, tính khối lượng tính cước, tính Standard hoặc Express, cộng các phụ phí như Remote, Island, COD và Insurance, sau đó lưu kết quả thành Quote Snapshot để các service khác sử dụng đúng mức giá đã báo cho khách.

---

## 2. Chức năng quản lý Rate Plan

Rate Plan là một phiên bản bảng giá.

Rate Plan có trạng thái DRAFT, ACTIVE hoặc RETIRED.

DRAFT được chỉnh sửa, ACTIVE đang dùng để quote, còn RETIRED là bảng giá đã ngừng sử dụng.

---

## 3. Chức năng tạo Rate Plan

Admin tạo Rate Plan mới ở trạng thái DRAFT.

Rate Plan chứa các cấu hình chung như volumetric divisor, remote surcharge, island rate, COD rule, insurance rule, giới hạn parcel và quote TTL.

---

## 4. Chức năng cập nhật Rate Plan

Chỉ DRAFT mới được sửa.

ACTIVE không sửa trực tiếp để tránh làm sai lịch sử quote.

---

## 5. Chức năng tạo Revision

Khi cần thay đổi bảng giá đang sử dụng, admin tạo revision mới.

Quote cũ vẫn giữ đúng revision đã dùng.

---

## 6. Chức năng quản lý Rate Rule

Rate Rule là giá cụ thể theo service level và pricing zone.

Có 6 combination:

```text
Standard Local
Standard Regional
Standard National

Express Local
Express Regional
Express National
```

Mỗi rule chứa Base Fee, Included Weight, Additional Weight Step, Step Fee và ETA.

---

## 7. Chức năng Activate Rate Plan

Hệ thống kiểm tra đủ rule và effective window hợp lệ trước khi activate.

Tại một thời điểm Pricing cần xác định được đúng một active Rate Plan.

---

## 8. Chức năng Retire Rate Plan

Rate Plan không còn sử dụng được chuyển sang RETIRED.

Dữ liệu vẫn giữ lại để truy lịch sử quote.

---

## 9. Chức năng tạo Quote

Pricing nhận nơi gửi, nơi nhận, parcel, cân nặng, kích thước, COD và declared value.

Sau đó Pricing chọn active Rate Plan, tính weight, gọi Network resolve location, xác định vùng và tính Standard lẫn Express.

Cuối cùng lưu Quote Snapshot.

---

## 10. Cách xác định Local, Regional, National

Pricing gọi Network lấy `serviceAreaId` và `regionId`.

Cùng Service Area thì Local.

Khác Service Area nhưng cùng Region thì Regional.

Khác Region thì National.

Cùng tỉnh chưa chắc Local vì một tỉnh có thể có nhiều Service Area.

---

## 11. Coverage Tier

Network trả thêm:

```text
STANDARD
REMOTE
ISLAND
```

Coverage Tier dùng để tính phụ phí và ETA.

Nó không phải Pricing Zone.

---

## 12. Bảng giá demo

### Standard

| Vùng | Giá 2kg đầu | Mỗi 0,5kg vượt |
|---|---:|---:|
| Local | 20.000đ | 4.000đ |
| Regional | 25.000đ | 5.000đ |
| National | 30.000đ | 6.000đ |

### Express

| Vùng | Giá 2kg đầu | Mỗi 0,5kg vượt |
|---|---:|---:|
| Local | 30.000đ | 6.000đ |
| Regional | 38.000đ | 7.000đ |
| National | 50.000đ | 9.000đ |

Đây là giá demo trong source.

---

## 13. Actual Weight

Actual Weight là cân nặng thật của parcel.

---

## 14. Volumetric Weight

Volumetric Weight là khối lượng quy đổi theo thể tích.

Công thức demo:

```text
Dài × Rộng × Cao / 5000
```

Ví dụ:

```text
50 × 40 × 30 cm
-> 12kg
```

---

## 15. Volumetric có phải phí riêng không?

Không.

Không có `Volumetric Fee`.

Volumetric chỉ dùng để xác định Chargeable Weight.

---

## 16. Chargeable Weight

Pricing lấy giá trị lớn hơn giữa Actual Weight và Volumetric Weight.

Sau đó làm tròn lên theo bước 0,5kg.

Ví dụ Actual 2kg nhưng Volumetric 12kg thì Chargeable Weight là 12kg.

---

## 17. Volumetric làm giá tăng thế nào?

Volumetric lớn làm Chargeable Weight lớn.

Chargeable Weight vượt 2kg nhiều hơn thì Additional Weight Fee tăng.

Vì vậy Volumetric ảnh hưởng gián tiếp tới phí cân nặng.

---

## 18. Transport Fee

Transport Fee bằng:

```text
Base Fee
+
Additional Weight Fee
```

Trong demo, 2kg đầu nằm trong Base Fee.

Phần vượt tính theo từng 0,5kg.

---

## 19. Ví dụ phí cân nặng

Standard National:

```text
Base Fee = 30.000đ cho 2kg đầu
Mỗi 0,5kg vượt = 6.000đ
```

Nếu Chargeable Weight là 2,5kg:

```text
Transport Fee = 36.000đ
```

---

## 20. Remote Surcharge

Demo:

```text
8.000đ / một đầu Remote
```

Một đầu Remote là 8.000đ.

Hai đầu Remote là 16.000đ.

ETA tăng khoảng một ngày.

---

## 21. Island Surcharge

Nếu có Island:

```text
20% × Transport Fee
```

ETA tăng khoảng hai ngày.

Logic hiện tại ưu tiên Island thay vì cộng thêm Remote surcharge.

---

## 22. COD Fee

COD đến 1 triệu được miễn phí.

Phần vượt 1 triệu tính 0,5%.

Minimum fee là 5.000đ.

---

## 23. Insurance Fee

Declared Value đến 1 triệu được miễn phí.

Phần vượt 1 triệu tính 0,5%.

---

## 24. Tổng giá

```text
TOTAL
=
Transport Fee
+ Remote/Island
+ COD
+ Insurance
```

Cuối cùng làm tròn lên 1.000đ.

---

## 25. Ví dụ tổng giá

Giả sử:

```text
Standard National
Chargeable Weight = 2,5kg
1 đầu Remote
COD = 2.000.000đ
Declared Value = 2.000.000đ
```

Kết quả:

```text
Transport = 36.000đ
Remote    = 8.000đ
COD       = 5.000đ
Insurance = 5.000đ
Total     = 54.000đ
```

---

## 26. Ví dụ Volumetric

Giả sử:

```text
Actual Weight = 2kg
Kích thước = 50 × 40 × 30 cm
Volumetric Weight = 12kg
```

Chargeable Weight là 12kg.

Nếu dùng Standard Local, Transport Fee theo bảng demo là:

```text
100.000đ
```

Phí tăng không phải vì có Volumetric Fee, mà vì Chargeable Weight tăng từ 2kg lên 12kg.

---

## 27. Standard và Express

Standard có giá thấp hơn và ETA dài hơn.

Express có giá cao hơn nhưng ETA nhanh hơn.

Cả hai dùng chung logic zone, weight, Remote/Island, COD và Insurance.

---

## 28. ETA

ETA cơ bản nằm trong Rate Rule.

Remote có thể cộng khoảng một ngày.

Island cộng khoảng hai ngày.

ETA của Pricing là ước lượng thương mại, không phải route departure thực tế.

---

## 29. Quote Expiry

Quote demo có TTL khoảng 30 phút.

Quote hết hạn không nên được dùng để tạo shipment mới.

---

## 30. Quote Snapshot

Snapshot lưu Rate Plan, Revision, Pricing Zone, Location Resolution, Parcel, Actual Weight, Volumetric Weight, Chargeable Weight, Standard, Express, Fee Breakdown, ETA và Expiry.

---

## 31. Internal Quote cho Shipment

Shipment lấy snapshot đã được Pricing tính.

Pricing không tính lại theo Rate Plan mới.

Mục tiêu là giữ đúng mức giá khách đã được báo.

---

## 32. Pricing Audit

Các thao tác tạo, sửa, revision, activate, retire hoặc thay Rate Rule được ghi audit.

---

## 33. Security

Pricing gọi Network bằng service JWT.

Ví dụ:

```text
sub   = pricing-service
aud   = network-service
scope = network.pricing.resolve
typ   = service
```

---

## 34. Câu trả lời hội đồng

**Volumetric có phải phí riêng không?**  
Không. Nó chỉ ảnh hưởng Chargeable Weight.

**Volumetric làm giá tăng bằng cách nào?**  
Nó làm Chargeable Weight tăng, dẫn đến Additional Weight Fee tăng.

**Local là gì?**  
Cùng Service Area.

**Regional là gì?**  
Khác Service Area nhưng cùng Region.

**National là gì?**  
Khác Region.

**Remote có phải vùng giá thứ tư không?**  
Không. Remote là Coverage Tier.

**Pricing có tính route không?**  
Không. Network xử lý route và capacity.

**Tại sao ACTIVE Rate Plan không sửa trực tiếp?**  
Để giữ lịch sử giá.

**Tại sao cần Quote Snapshot?**  
Để Shipment dùng đúng giá đã báo cho khách.

---

## 35. File code quan trọng

Các file nên nhớ là `QuoteService.kt`, `QuoteCalculator.kt`, `RatePlanService.kt`, `PricingDemoRateSeeder.kt`, `NetworkLocationClient.kt`, `RatePlan.kt`, `RateRule.kt`, `QuoteRequest.kt`, `QuoteParcel.kt`, `QuoteOption.kt`, `PricingQuoteController.kt`, `RatePlanController.kt`, `InternalPricingQuoteController.kt` và `SecurityConfig.kt`.
