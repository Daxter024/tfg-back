# Disabled migration templates

This directory is **NOT** on the Flyway classpath. Anything stored here is
informational — it never runs at service boot.

Today it contains exactly one file:

- `V99__migrate_to_timescaledb.sql.template` — the migration to convert
  `sensor_reading` into a Timescale hypertable plus continuous aggregates.

## When to activate

Plan decision D3 keeps iot-service on vanilla `postgres:14` until the TFG load
shows the plain views can no longer keep up. When that happens:

1. In `docker-compose.yml` (root and standalone) change `iot-db.image` from
   `postgres:14` to `timescale/timescaledb:2.14.2-pg14`.
2. Rename this file to `V3__migrate_to_timescaledb.sql` and move it to
   `../migration/`. The `.template` suffix and `V99` numbering are both
   deliberate: the suffix keeps it visible as "needs work" in IDEs, the V99
   guarantees a clean Flyway sequence regardless of how many V2/V3 land here
   in the meantime.
3. Restart iot-service. Flyway applies V3, the migration runs once, and the
   hourly/daily views become continuous aggregates.

No Java change is required — the SQL view names are identical, only their
implementation switches.
