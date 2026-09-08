// Nhân vật và thông tin liên hệ giả lập, chỉ dùng với Mailpit/local.
export const scenario = {
  code: "PAVEX-MAU-HN-HCM-01",
  routeCode: "MAU-HN-HCM-DUONGBO",
  hubs: ["HN-HOANKIEM", "HN-GW", "HCM-GW", "HCM-SAIGON"],
  merchant: { email: "minhanh@demo.pavex.local", firstName: "Minh Anh", lastName: "Nguyễn",
    displayName: "Nguyễn Minh Anh", gender: "FEMALE", dateOfBirth: "1995-05-12",
    businessName: "Cửa hàng May Mộc", contactPhone: "0900000001" },
  sender: { contactName: "Nguyễn Minh Anh", contactPhone: "0900000001",
    addressLine: "12 Hàng Bông (địa chỉ kiểm thử)", provinceCode: "01", wardCode: "00070" },
  recipient: { contactName: "Phạm Thảo Vy", contactPhone: "0900000002",
    addressLine: "25 Nguyễn Huệ (địa chỉ kiểm thử)", provinceCode: "79", wardCode: "26740" },
};

const person = (email, lastName, firstName, gender, kind, hubCode, dispatcherAccessLevel) => ({
  email: `${email}@demo.pavex.local`, lastName, firstName, displayName: `${lastName} ${firstName}`,
  gender, dateOfBirth: "1993-06-15", kind, hubCode, dispatcherAccessLevel,
});

export const staff = [
  person("quocbao", "Trần", "Quốc Bảo", "MALE", "DISPATCHER", null, "GLOBAL"),
  person("vanhung", "Nguyễn", "Văn Hùng", "MALE", "DISPATCHER", "HN-HOANKIEM", "HUB_MANAGER"),
  person("ngocmai", "Trần", "Ngọc Mai", "FEMALE", "DISPATCHER", "HN-HOANKIEM", "HUB"),
  person("hoangnam", "Lê", "Hoàng Nam", "MALE", "COURIER", "HN-HOANKIEM"),
  person("thanhha", "Phạm", "Thanh Hà", "FEMALE", "WAREHOUSE_OPERATOR", "HN-HOANKIEM"),
  person("ducthinh", "Vũ", "Đức Thịnh", "MALE", "LINE_HAUL_DRIVER", "HN-HOANKIEM"),
  person("tuananh", "Bùi", "Tuấn Anh", "MALE", "WAREHOUSE_OPERATOR", "HN-GW"),
  person("minhduc", "Đỗ", "Minh Đức", "MALE", "LINE_HAUL_DRIVER", "HN-GW"),
  person("hongnhung", "Lê", "Hồng Nhung", "FEMALE", "WAREHOUSE_OPERATOR", "HCM-GW"),
  person("quanghuy", "Nguyễn", "Quang Huy", "MALE", "LINE_HAUL_DRIVER", "HCM-GW"),
  person("khanhlinh", "Võ", "Khánh Linh", "FEMALE", "DISPATCHER", "HCM-SAIGON", "HUB_MANAGER"),
  person("baotran", "Trần", "Bảo Trân", "FEMALE", "DISPATCHER", "HCM-SAIGON", "HUB"),
  person("minhchau", "Phan", "Minh Châu", "FEMALE", "WAREHOUSE_OPERATOR", "HCM-SAIGON"),
  person("giahao", "Lâm", "Gia Hào", "MALE", "COURIER", "HCM-SAIGON"),
];

export const roleLabel = (p) => ({
  GLOBAL: "Quản lý vận hành toàn mạng", HUB_MANAGER: "Quản lý bưu cục", HUB: "Nhân viên văn phòng / điều phối",
}[p.dispatcherAccessLevel] ?? { COURIER: "Nhân viên giao nhận", WAREHOUSE_OPERATOR: "Nhân viên kho",
  LINE_HAUL_DRIVER: "Tài xế trung chuyển" }[p.kind]);
