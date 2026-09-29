-- Lets several shipment-service instances publish the outbox to RabbitMQ without sending a
-- message twice: a dispatcher leases a batch, publishes it, then marks it PUBLISHED.
-- FAILED now means dead-lettered after the maximum number of attempts.
ALTER TABLE shipments.outbox_events
    ADD COLUMN lock_id UUID NULL,
    ADD COLUMN locked_until TIMESTAMPTZ NULL;
