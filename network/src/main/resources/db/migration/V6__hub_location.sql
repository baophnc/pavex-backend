-- Where each hub is on the map: trucks are drawn between hubs and the fleet simulator drives
-- from one hub's coordinates to the next. Optional, so hubs without them still work.
ALTER TABLE network.hubs
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD CONSTRAINT ck_hub_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL) OR
        (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    );
