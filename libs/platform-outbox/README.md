# Pavex Platform Outbox

The relay mechanics of the transactional outbox, shared by Auth, Shipment, Operations and Partners.
Each service keeps its own outbox table and migrations, its claim query and the way it sends a row;
this module only runs the loop around them.

- `OutboxRelay`: send each claimed row, then mark it published, or schedule a retry after
  `RetryBackoff` (base delay doubling up to a cap), or dead-letter it once the attempt reaches
  `maxAttempts` (`null`: retry forever). The service says what the attempt number of a row is and
  how its row records each outcome (`OutboxRelayStore`).
- `JdbcOutboxLease` + `OutboxLeaseSettings`: for lease-based outboxes (Shipment, Operations). Rows are
  picked `FOR UPDATE SKIP LOCKED`, stamped with `lock_id`/`locked_until` and the attempt counted, all in
  one short transaction; they are sent outside it and marked only while the lease is still ours.
- `RabbitOutboxPublisher`: publish with the service's headers and wait for the publisher confirm; a nack
  (and optionally an unroutable return) fails the attempt.

| Service | Claiming | Sent by | Dead-letter |
|---|---|---|---|
| Shipment | lease, per-shipment order | RabbitMQ, confirms | `FAILED` after 8 |
| Operations | lease, per-aggregate order | RabbitMQ, confirms | `dead_lettered_at` after 8; admin requeue/discard |
| Auth | row locks for the transaction | RabbitMQ, confirms + returns, encrypted payload | `DEAD` after 100 |
| Partners | row locks for the transaction, per-user order, superseded rows skipped | HTTP to Identity | never |

Consumer-side de-duplication (processed-message tables) stays in each consumer.
