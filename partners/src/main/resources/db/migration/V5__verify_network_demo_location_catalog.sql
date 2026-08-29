-- Network demo scope guardrail.
--
-- V3 already seeds the complete post-2025 Vietnamese administrative catalog.
-- Network demo data intentionally relies on the canonical Partners-owned rows
-- instead of duplicating province/ward data inside the Network service.
--
-- This migration freezes the minimum catalog contract needed by the demo
-- topology that will be created in the following Network seed patches:
--   01 = Ha Noi       : 126 commune-level units (51 wards, 75 communes)
--   48 = Da Nang      :  94 commune-level units (23 wards, 70 communes, 1 special region)
--   79 = Ho Chi Minh  : 168 commune-level units (113 wards, 54 communes, 1 special region)
-- Total               : 388 commune-level units.
--
-- Fail fast rather than allowing Network coverage to be generated against an
-- incomplete or pre-merger location catalog.

DO $$
DECLARE
    actual INTEGER;
BEGIN
    SELECT COUNT(*) INTO actual
    FROM partners.provinces
    WHERE (code = '01' AND name = 'Hà Nội' AND code_name = 'ha_noi')
       OR (code = '48' AND name = 'Đà Nẵng' AND code_name = 'da_nang')
       OR (code = '79' AND name = 'Hồ Chí Minh' AND code_name = 'ho_chi_minh');

    IF actual <> 3 THEN
        RAISE EXCEPTION
            'Network demo location catalog requires provinces 01=Hà Nội, 48=Đà Nẵng, 79=Hồ Chí Minh; matched % of 3',
            actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code IN ('01', '48', '79');

    IF actual <> 388 THEN
        RAISE EXCEPTION
            'Network demo location catalog requires exactly 388 commune-level units across 01/48/79; found %',
            actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM partners.wards WHERE province_code = '01';
    IF actual <> 126 THEN
        RAISE EXCEPTION 'Hà Nội location catalog requires 126 units; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '01' AND administrative_unit_id = 3;
    IF actual <> 51 THEN
        RAISE EXCEPTION 'Hà Nội location catalog requires 51 wards; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '01' AND administrative_unit_id = 4;
    IF actual <> 75 THEN
        RAISE EXCEPTION 'Hà Nội location catalog requires 75 communes; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM partners.wards WHERE province_code = '48';
    IF actual <> 94 THEN
        RAISE EXCEPTION 'Đà Nẵng location catalog requires 94 units; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '48' AND administrative_unit_id = 3;
    IF actual <> 23 THEN
        RAISE EXCEPTION 'Đà Nẵng location catalog requires 23 wards; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '48' AND administrative_unit_id = 4;
    IF actual <> 70 THEN
        RAISE EXCEPTION 'Đà Nẵng location catalog requires 70 communes; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '48' AND administrative_unit_id = 5;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'Đà Nẵng location catalog requires 1 special administrative region; found %', actual;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM partners.wards
        WHERE code = '20333'
          AND province_code = '48'
          AND name = 'Hoàng Sa'
          AND administrative_unit_id = 5
    ) THEN
        RAISE EXCEPTION 'Đà Nẵng location catalog is missing special administrative region 20333=Hoàng Sa';
    END IF;

    SELECT COUNT(*) INTO actual FROM partners.wards WHERE province_code = '79';
    IF actual <> 168 THEN
        RAISE EXCEPTION 'Hồ Chí Minh location catalog requires 168 units; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '79' AND administrative_unit_id = 3;
    IF actual <> 113 THEN
        RAISE EXCEPTION 'Hồ Chí Minh location catalog requires 113 wards; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '79' AND administrative_unit_id = 4;
    IF actual <> 54 THEN
        RAISE EXCEPTION 'Hồ Chí Minh location catalog requires 54 communes; found %', actual;
    END IF;

    SELECT COUNT(*) INTO actual
    FROM partners.wards
    WHERE province_code = '79' AND administrative_unit_id = 5;
    IF actual <> 1 THEN
        RAISE EXCEPTION 'Hồ Chí Minh location catalog requires 1 special administrative region; found %', actual;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM partners.wards
        WHERE code = '26732'
          AND province_code = '79'
          AND name = 'Côn Đảo'
          AND administrative_unit_id = 5
    ) THEN
        RAISE EXCEPTION 'Hồ Chí Minh location catalog is missing special administrative region 26732=Côn Đảo';
    END IF;
END $$;
