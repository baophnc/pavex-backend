package com.pavex.network.seed

import com.pavex.network.entity.CoverageTier
import org.springframework.core.io.ClassPathResource

data class DemoGatewaySeed(
    val code: String,
    val name: String,
    val provinceCode: String,
    val wardCode: String,
)

data class DemoServiceAreaSeed(
    val code: String,
    val name: String,
    val regionCode: String,
    val provinceCode: String,
    val hubCode: String,
    val hubName: String,
    val hubWardCode: String,
    val gatewayCode: String,
)

data class DemoCoverageSeed(
    val provinceCode: String,
    val wardCode: String,
    val serviceAreaCode: String,
    val tier: CoverageTier,
)

object NetworkDemoGeographyData {
    const val RESOURCE_PATH = "seed/network-demo-geography.csv"

    val gateways = listOf(
        DemoGatewaySeed("HN-GW", "Hà Nội", "01", "00070"),
        DemoGatewaySeed("DN-GW", "Đà Nẵng", "48", "20242"),
        DemoGatewaySeed("HCM-GW", "Hồ Chí Minh", "79", "26740"),
    )

    val serviceAreas = listOf(
        // Hà Nội — 126 commune-level units across 11 operating areas.
        DemoServiceAreaSeed("HN-CENTRAL", "Trung tâm Hà Nội", "NORTH", "01", "HN-HOANKIEM", "Hoàn Kiếm", "00070", "HN-GW"),
        DemoServiceAreaSeed("HN-SOUTHEAST", "Nam - Đông Hà Nội", "NORTH", "01", "HN-HOANGMAI", "Hoàng Mai", "00331", "HN-GW"),
        DemoServiceAreaSeed("HN-NORTH", "Bắc Hà Nội", "NORTH", "01", "HN-DONGANH", "Đông Anh", "00454", "HN-GW"),
        DemoServiceAreaSeed("HN-WEST", "Tây Hà Nội", "NORTH", "01", "HN-TULIEM", "Từ Liêm", "00592", "HN-GW"),
        DemoServiceAreaSeed("HN-MELINH", "Mê Linh", "NORTH", "01", "HN-MELINH", "Mê Linh", "09022", "HN-GW"),
        DemoServiceAreaSeed("HN-HADONG", "Hà Đông", "NORTH", "01", "HN-HADONG", "Hà Đông", "09556", "HN-GW"),
        DemoServiceAreaSeed("HN-SONTAY-BAVI", "Sơn Tây - Ba Vì", "NORTH", "01", "HN-SONTAY", "Sơn Tây", "09574", "HN-GW"),
        DemoServiceAreaSeed("HN-HOAIDUC-PHUCTHO", "Hoài Đức - Phúc Thọ", "NORTH", "01", "HN-HOAIDUC", "Hoài Đức", "09832", "HN-GW"),
        DemoServiceAreaSeed("HN-HOALAC-QUOCOAI", "Hòa Lạc - Quốc Oai", "NORTH", "01", "HN-HOALAC", "Hoà Lạc", "09988", "HN-GW"),
        DemoServiceAreaSeed("HN-CHUONGMY-THANHOAI", "Chương Mỹ - Thanh Oai", "NORTH", "01", "HN-CHUONGMY", "Chương Mỹ", "10015", "HN-GW"),
        DemoServiceAreaSeed("HN-SOUTH-RURAL", "Nam Hà Nội", "NORTH", "01", "HN-PHUXUYEN", "Phú Xuyên", "10273", "HN-GW"),

        // Đà Nẵng (post-2025 boundary) — 94 units across 8 operating areas.
        DemoServiceAreaSeed("DN-URBAN", "Đô thị Đà Nẵng", "CENTRAL", "48", "DN-HAICHAU", "Hải Châu", "20242", "DN-GW"),
        DemoServiceAreaSeed("DN-TAMKY", "Tam Kỳ - Phú Ninh", "CENTRAL", "48", "DN-TAMKY", "Tam Kỳ", "20341", "DN-GW"),
        DemoServiceAreaSeed("DN-HOIAN", "Hội An", "CENTRAL", "48", "DN-HOIAN", "Hội An", "20410", "DN-GW"),
        DemoServiceAreaSeed("DN-NORTHWEST", "Tây Bắc Đà Nẵng", "CENTRAL", "48", "DN-DAILOC", "Đại Lộc", "20500", "DN-GW"),
        DemoServiceAreaSeed("DN-DIENBAN-DUYXUYEN", "Điện Bàn - Duy Xuyên", "CENTRAL", "48", "DN-DIENBAN", "Điện Bàn", "20551", "DN-GW"),
        DemoServiceAreaSeed("DN-HIGHLANDS", "Miền núi phía Tây", "CENTRAL", "48", "DN-KHAMDUC", "Khâm Đức", "20722", "DN-GW"),
        DemoServiceAreaSeed("DN-THANGBINH-TRAMY", "Thăng Bình - Trà My", "CENTRAL", "48", "DN-TRAMY", "Trà My", "20900", "DN-GW"),
        DemoServiceAreaSeed("DN-NUITHANH", "Núi Thành", "CENTRAL", "48", "DN-NUITHANH", "Núi Thành", "20965", "DN-GW"),

        // Hồ Chí Minh (post-2025 boundary) — 168 units across 18 operating areas.
        DemoServiceAreaSeed("HCM-THUDAUMOT", "Thủ Dầu Một", "SOUTH", "79", "HCM-THUDAUMOT", "Thủ Dầu Một", "25747", "HCM-GW"),
        DemoServiceAreaSeed("HCM-BENCAT-DAUTIENG", "Bến Cát - Dầu Tiếng", "SOUTH", "79", "HCM-BENCAT", "Bến Cát", "25813", "HCM-GW"),
        DemoServiceAreaSeed("HCM-PHUGIAO-TANUYEN", "Phú Giáo - Tân Uyên", "SOUTH", "79", "HCM-TANUYEN", "Tân Uyên", "25888", "HCM-GW"),
        DemoServiceAreaSeed("HCM-DIAN-THUANAN", "Dĩ An - Thuận An", "SOUTH", "79", "HCM-DIAN", "Dĩ An", "25942", "HCM-GW"),
        DemoServiceAreaSeed("HCM-VUNGTAU", "Vũng Tàu", "SOUTH", "79", "HCM-VUNGTAU", "Vũng Tàu", "26506", "HCM-GW"),
        DemoServiceAreaSeed("HCM-BARIA-XUYENMOC", "Bà Rịa - Xuyên Mộc", "SOUTH", "79", "HCM-BARIA", "Bà Rịa", "26560", "HCM-GW"),
        DemoServiceAreaSeed("HCM-PHUMY-CONDAO", "Phú Mỹ - Côn Đảo", "SOUTH", "79", "HCM-PHUMY", "Phú Mỹ", "26704", "HCM-GW"),
        DemoServiceAreaSeed("HCM-CENTRAL", "Trung tâm Hồ Chí Minh", "SOUTH", "79", "HCM-SAIGON", "Sài Gòn", "26740", "HCM-GW"),
        DemoServiceAreaSeed("HCM-THUDUC", "Thủ Đức", "SOUTH", "79", "HCM-THUDUC", "Thủ Đức", "26824", "HCM-GW"),
        DemoServiceAreaSeed("HCM-GOVAP-BINHTHANH", "Gò Vấp - Bình Thạnh", "SOUTH", "79", "HCM-BINHTHANH", "Bình Thạnh", "26929", "HCM-GW"),
        DemoServiceAreaSeed("HCM-TANBINH-TANPHU", "Tân Bình - Tân Phú", "SOUTH", "79", "HCM-TANBINH", "Tân Bình", "27004", "HCM-GW"),
        DemoServiceAreaSeed("HCM-EAST", "Đông Hồ Chí Minh", "SOUTH", "79", "HCM-ANKHANH", "An Khánh", "27094", "HCM-GW"),
        DemoServiceAreaSeed("HCM-CENTRAL-SOUTH", "Trung tâm phía Nam", "SOUTH", "79", "HCM-CHOLON", "Chợ Lớn", "27343", "HCM-GW"),
        DemoServiceAreaSeed("HCM-BINHTAN", "Bình Tân", "SOUTH", "79", "HCM-BINHTAN", "Bình Tân", "27442", "HCM-GW"),
        DemoServiceAreaSeed("HCM-SOUTH", "Nam Hồ Chí Minh", "SOUTH", "79", "HCM-TANTHUAN", "Tân Thuận", "27478", "HCM-GW"),
        DemoServiceAreaSeed("HCM-CUCHI-HOCMON", "Củ Chi - Hóc Môn", "SOUTH", "79", "HCM-CUCHI", "Củ Chi", "27553", "HCM-GW"),
        DemoServiceAreaSeed("HCM-BINHCHANH", "Bình Chánh", "SOUTH", "79", "HCM-BINHCHANH", "Bình Chánh", "27637", "HCM-GW"),
        DemoServiceAreaSeed("HCM-NHABE-CANGIO", "Nhà Bè - Cần Giờ", "SOUTH", "79", "HCM-CANGIO", "Cần Giờ", "27664", "HCM-GW"),
    )

    fun coverages(): List<DemoCoverageSeed> {
        val rows = ClassPathResource(RESOURCE_PATH).inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines
                .drop(1)
                .filter { it.isNotBlank() }
                .mapIndexed { index, line ->
                    val columns = line.split(',')
                    require(columns.size == 4) { "Invalid demo geography row ${index + 2}: $line" }
                    DemoCoverageSeed(
                        provinceCode = columns[0],
                        wardCode = columns[1],
                        serviceAreaCode = columns[2],
                        tier = CoverageTier.valueOf(columns[3]),
                    )
                }
                .toList()
        }

        validate(rows)
        return rows
    }

    private fun validate(rows: List<DemoCoverageSeed>) {
        require(rows.size == 388) { "Demo geography must contain 388 coverage rows, found ${rows.size}" }
        require(rows.map { it.wardCode }.distinct().size == rows.size) { "Demo geography contains duplicate ward codes" }

        val expectedCounts = mapOf("01" to 126, "48" to 94, "79" to 168)
        require(rows.groupingBy { it.provinceCode }.eachCount() == expectedCounts) {
            "Demo geography province counts do not match the Partners location catalog contract"
        }

        val areaCodes = serviceAreas.map { it.code }.toSet()
        require(rows.all { it.serviceAreaCode in areaCodes }) { "Demo geography references an unknown service area" }
        require(serviceAreas.map { it.code }.distinct().size == serviceAreas.size) { "Duplicate service area codes in demo seed" }
        require(serviceAreas.map { it.hubCode }.distinct().size == serviceAreas.size) { "Duplicate local hub codes in demo seed" }
    }
}
