# Network Service — Bản thuyết trình đầy đủ

## 1. Giới thiệu tổng quan

Ở **Network Service**, chức năng chính là quản lý toàn bộ mạng lưới vận chuyển.

Module này quản lý Region, Hub, Hub hierarchy, Service Area, Coverage, Coverage Tier, Lane, Transport Mode, Schedule, Route Template, Route Planning, Earliest Arrival và Capacity Reservation.

Network còn cung cấp internal API để Pricing resolve location.

Nói ngắn gọn:

> Network Service quản lý topology vận chuyển, tìm route và quản lý capacity của từng departure.

---

## 2. Chức năng Region

Region là vùng vận hành lớn.

Ví dụ demo có NORTH, CENTRAL và SOUTH.

Region không phải tỉnh/thành hành chính.

---

## 3. Chức năng Hub

Hub là điểm logistics.

Hub có thể có parentHubId để mô tả hierarchy.

parentHubId không quyết định tuyến vận chuyển.

---

## 4. Chức năng Service Area

Service Area là vùng phục vụ nhỏ hơn Region.

Mỗi Service Area thuộc Region và có Primary Hub.

Pricing dùng Service Area và Region để xác định Local, Regional hay National.

---

## 5. Chức năng Coverage

Coverage map province hoặc ward vào Service Area.

Nếu ward có mapping riêng thì dùng ward trước.

Nếu không có thì fallback province.

---

## 6. Coverage Tier

Coverage có thể là:

```text
STANDARD
REMOTE
ISLAND
```

Pricing dùng tier này để tính phụ phí.

---

## 7. Chức năng Resolve Location

Network nhận provinceCode và wardCode.

Sau khi validate qua Partners, Network trả:

```text
serviceAreaId
regionId
primaryHubId
coverageTier
```

Pricing dùng response này để tính vùng giá.

---

## 8. Chức năng Lane

Lane là tuyến có hướng từ Hub A đến Hub B.

Lane có thể chứa transport mode, distance, transit time, max shipment weight và status.

---

## 9. Transport Mode

Các mode có thể gồm ROAD, AIR, RAIL và SEA.

---

## 10. Chức năng Schedule

Mỗi lane có thể có nhiều schedule.

Schedule chứa weekday, departure time, effective window, capacity và status.

---

## 11. Chức năng Next Departure

Dựa vào schedule và thời gian hàng sẵn sàng, Network tìm departure phù hợp tiếp theo.

---

## 12. Chức năng Route Planning

Network nhận origin, destination, readyAt và thông tin shipment để tìm route.

Planning có thể ưu tiên route template hoặc dùng graph.

---

## 13. Earliest Arrival

Thuật toán không chỉ dùng distance.

Nó xét waiting time, transfer buffer, transit time, schedule và capacity.

Vì vậy route ngắn nhất về kilomet chưa chắc là route đến sớm nhất.

---

## 14. Planning Mode

Có các mode như:

```text
PREFER_TEMPLATE
GRAPH_ONLY
TEMPLATE_ONLY
```

PREFER_TEMPLATE ưu tiên template nhưng có thể fallback graph.

GRAPH_ONLY chỉ dùng graph.

TEMPLATE_ONLY chỉ dùng template.

---

## 15. Route Template

Route Template là tuyến ưu tiên do operator cấu hình.

Template chứa ordered lanes, priority, status và revision.

Revision giúp biết đúng phiên bản route template đã dùng.

---

## 16. Local Route

Nếu origin và destination cùng Primary Hub thì Network có thể trả route dạng LOCAL mà không cần inter-hub legs.

---

## 17. Chức năng Capacity

Capacity nằm ở từng departure cụ thể.

Ví dụ cùng lane nhưng chuyến 8 giờ và 18 giờ có capacity khác nhau.

---

## 18. Chức năng Reserve Capacity

Shipment reserve capacity trên từng leg.

Network lock rồi kiểm tra lại remaining capacity trước khi reserve.

---

## 19. Chức năng Consume Capacity

Khi shipment thực sự đi qua leg, reservation chuyển sang CONSUMED.

Capacity đó vẫn được tính là đã sử dụng.

---

## 20. Chức năng Release Capacity

Nếu route không còn dùng, reservation chuyển sang RELEASED và capacity được trả lại.

---

## 21. Chống Oversell

Nếu nhiều request cùng reserve một departure, Network dùng lock để tránh tổng reservation vượt capacity.

---

## 22. Re-plan khi Departure đầy

Nếu departure đầy giữa lúc plan và reserve, Network có thể exclude departure đó rồi plan lại tuyến hoặc chuyến khác.

---

## 23. Network gọi Partners để làm gì?

Network sử dụng location catalog của Partners để validate provinceCode và wardCode.

---

## 24. Internal API cho Pricing

Pricing gọi Network bằng service JWT để resolve location.

Ví dụ token có:

```text
sub   = pricing-service
aud   = network-service
scope = network.pricing.resolve
typ   = service
```

---

## 25. Database chính

Các bảng quan trọng gồm `regions`, `hubs`, `service_areas`, `service_area_coverages`, `hub_lanes`, `lane_schedules`, `route_templates`, `route_template_legs`, `lane_capacity_reservations` và `audit_logs`.

---

## 26. Câu trả lời hội đồng

**Region có phải tỉnh không?**  
Không. Region là vùng vận hành.

**Tại sao Ward coverage ưu tiên Province coverage?**  
Để có thể override chi tiết cho khu vực đặc biệt.

**Route tối ưu theo gì?**  
Theo arrival time thực tế, không chỉ distance.

**Capacity nằm ở đâu?**  
Ở từng departure cụ thể.

**Pricing gọi Network để làm gì?**  
Để lấy Service Area, Region và Coverage Tier.

---

## 27. File code quan trọng

Các file nên nhớ là `NetworkRegionService.kt`, `HubService.kt`, `ServiceAreaService.kt`, `CoverageService.kt`, `NetworkResolverService.kt`, `HubLaneService.kt`, `LaneScheduleService.kt`, `LaneScheduleCalculator.kt`, `NetworkRoutePlanner.kt`, `EarliestArrivalPathFinder.kt`, `RouteTemplateService.kt`, `RouteTemplateJourneyPlanner.kt`, `NetworkCapacityService.kt`, `NetworkTransactionLockService.kt`, `LocationCatalogClient.kt`, `InternalNetworkController.kt` và `SecurityConfig.kt`.
