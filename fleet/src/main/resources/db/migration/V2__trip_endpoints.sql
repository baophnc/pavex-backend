-- Where the truck leaves from and heads to, copied from the hubs when the trip starts: the map
-- draws the lane and the fleet simulator drives along it. Null when a hub has no coordinates.
ALTER TABLE fleet.trips
    ADD COLUMN from_lat DOUBLE PRECISION,
    ADD COLUMN from_lng DOUBLE PRECISION,
    ADD COLUMN to_lat   DOUBLE PRECISION,
    ADD COLUMN to_lng   DOUBLE PRECISION;
