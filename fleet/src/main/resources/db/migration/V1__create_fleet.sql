CREATE SCHEMA IF NOT EXISTS fleet;

-- One truck run of one line-haul driver on one lane departure. Hub and lane details are copied
-- when the trip starts so the trip still reads correctly if the network changes later.
CREATE TABLE fleet.trips (
    id                   UUID PRIMARY KEY,
    driver_user_id       UUID         NOT NULL,
    driver_email         VARCHAR(320),
    lane_id              UUID         NOT NULL,
    lane_code            VARCHAR(80)  NOT NULL,
    from_hub_id          UUID         NOT NULL,
    from_hub_code        VARCHAR(50)  NOT NULL,
    from_hub_name        VARCHAR(200) NOT NULL,
    to_hub_id            UUID         NOT NULL,
    to_hub_code          VARCHAR(50)  NOT NULL,
    to_hub_name          VARCHAR(200) NOT NULL,
    planned_departure_at TIMESTAMPTZ  NOT NULL,
    planned_arrival_at   TIMESTAMPTZ  NOT NULL,
    distance_km          NUMERIC(10, 2),
    vehicle_plate        VARCHAR(20),
    status               VARCHAR(20)  NOT NULL,
    start_source         VARCHAR(20)  NOT NULL,
    started_at           TIMESTAMPTZ  NOT NULL,
    end_source           VARCHAR(20),
    arrived_at           TIMESTAMPTZ,
    last_lat             DOUBLE PRECISION,
    last_lng             DOUBLE PRECISION,
    last_accuracy_m      DOUBLE PRECISION,
    last_speed_mps       DOUBLE PRECISION,
    last_heading_deg     DOUBLE PRECISION,
    last_battery_pct     INTEGER,
    last_mocked          BOOLEAN      NOT NULL DEFAULT FALSE,
    last_recorded_at     TIMESTAMPTZ,
    last_received_at     TIMESTAMPTZ,
    point_count          BIGINT       NOT NULL DEFAULT 0,
    travelled_meters     DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_trip_driver_departure UNIQUE (driver_user_id, lane_id, planned_departure_at),
    CONSTRAINT ck_trip_status CHECK (status IN ('IN_PROGRESS', 'ARRIVED')),
    CONSTRAINT ck_trip_start_source CHECK (start_source IN ('DRIVER', 'DEPART_SCAN')),
    CONSTRAINT ck_trip_end_source CHECK (end_source IS NULL OR end_source IN ('DRIVER', 'ALL_RECEIVED')),
    CONSTRAINT ck_trip_arrival CHECK ((status = 'ARRIVED') = (arrived_at IS NOT NULL))
);

-- A driver runs at most one trip at a time.
CREATE UNIQUE INDEX ux_trip_driver_in_progress ON fleet.trips (driver_user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_trip_status_from_hub ON fleet.trips (status, from_hub_id);
CREATE INDEX ix_trip_status_to_hub ON fleet.trips (status, to_hub_id);
CREATE INDEX ix_trip_arrived_at ON fleet.trips (arrived_at) WHERE status = 'ARRIVED';

-- Raw GPS points, one partition per UTC day (created ahead by the service, dropped after the
-- retention period). The primary key makes a resent batch harmless.
CREATE TABLE fleet.trip_locations (
    trip_id     UUID             NOT NULL,
    seq         BIGINT           NOT NULL,
    recorded_at TIMESTAMPTZ      NOT NULL,
    received_at TIMESTAMPTZ      NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    accuracy_m  REAL             NOT NULL,
    speed_mps   REAL,
    heading_deg REAL,
    battery_pct SMALLINT,
    mocked      BOOLEAN          NOT NULL DEFAULT FALSE,
    PRIMARY KEY (trip_id, seq, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE fleet.trip_locations_default PARTITION OF fleet.trip_locations DEFAULT;
CREATE INDEX ix_trip_locations_trip_time ON fleet.trip_locations (trip_id, recorded_at);

-- Drivers must accept the location terms before their first trip; a new terms version asks again.
CREATE TABLE fleet.driver_consents (
    user_id       UUID PRIMARY KEY,
    terms_version VARCHAR(20) NOT NULL,
    accepted_at   TIMESTAMPTZ NOT NULL
);

-- Who looked at whose GPS track, and when.
CREATE TABLE fleet.trip_access_log (
    id             UUID PRIMARY KEY,
    trip_id        UUID        NOT NULL,
    viewer_user_id UUID        NOT NULL,
    viewed_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_trip_access_log_trip ON fleet.trip_access_log (trip_id, viewed_at);
