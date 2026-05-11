package com.agro.iotservice.repository;

import com.agro.iotservice.model.AggregatedBucket;
import com.agro.iotservice.model.SensorReading;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SensorReadingRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<SensorReading> rawMapper = (rs, n) -> new SensorReading(
            (UUID) rs.getObject("sensor_id"),
            rs.getTimestamp("recorded_at").toInstant(),
            rs.getBigDecimal("value")
    );

    private final RowMapper<AggregatedBucket> bucketMapper = (rs, n) -> new AggregatedBucket(
            (UUID) rs.getObject("sensor_id"),
            rs.getTimestamp("bucket").toInstant(),
            rs.getBigDecimal("avg_value"),
            rs.getBigDecimal("min_value"),
            rs.getBigDecimal("max_value"),
            rs.getLong("samples")
    );

    /**
     * Inserts one reading idempotently. ON CONFLICT (sensor_id, recorded_at)
     * DO NOTHING guarantees the same (sensor, timestamp) is never recorded
     * twice — devices and the simulator can retry safely.
     *
     * @return number of rows actually inserted (0 or 1).
     */
    public int insertIfAbsent(UUID sensorId, Instant recordedAt, BigDecimal value) {
        return jdbc.update("""
                INSERT INTO sensor_reading (sensor_id, recorded_at, value)
                VALUES (?, ?, ?)
                ON CONFLICT (sensor_id, recorded_at) DO NOTHING
                """, sensorId, Timestamp.from(recordedAt), value);
    }

    public List<SensorReading> findRaw(UUID sensorId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT sensor_id, recorded_at, value
                  FROM sensor_reading
                 WHERE sensor_id = ?
                   AND recorded_at >= ?
                   AND recorded_at < ?
                 ORDER BY recorded_at ASC
                """, rawMapper, sensorId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<AggregatedBucket> findHourly(UUID sensorId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT sensor_id, bucket, avg_value, min_value, max_value, samples
                  FROM sensor_reading_hourly
                 WHERE sensor_id = ?
                   AND bucket >= date_trunc('hour', ?::timestamptz)
                   AND bucket <  ?::timestamptz
                 ORDER BY bucket ASC
                """, bucketMapper, sensorId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<AggregatedBucket> findDaily(UUID sensorId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT sensor_id, bucket, avg_value, min_value, max_value, samples
                  FROM sensor_reading_daily
                 WHERE sensor_id = ?
                   AND bucket >= date_trunc('day', ?::timestamptz)
                   AND bucket <  ?::timestamptz
                 ORDER BY bucket ASC
                """, bucketMapper, sensorId, Timestamp.from(from), Timestamp.from(to));
    }
}
