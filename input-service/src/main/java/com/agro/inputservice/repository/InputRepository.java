package com.agro.inputservice.repository;

import com.agro.inputservice.model.Input;
import com.agro.inputservice.model.InputCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a la tabla {@code input} (catalogo de insumos) y a la vista
 * {@code input_with_stock} cuando se necesita el stock calculado.
 */
@Repository
@RequiredArgsConstructor
public class InputRepository {

    private final JdbcTemplate jdbc;

    private static final String SELECT_WITH_STOCK = """
            SELECT i.id, i.name, i.category, i.unit, i.low_stock_threshold,
                   i.supplier, i.notes, i.created_by, i.created_at, i.updated_at,
                   i.deleted_at,
                   COALESCE((SELECT SUM(CASE WHEN m.kind='IN' THEN m.quantity ELSE -m.quantity END)
                               FROM input_movement m WHERE m.input_id = i.id), 0) AS current_stock
              FROM input i
            """;

    private final RowMapper<Input> inputRowMapper = (rs, n) -> new Input(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            InputCategory.valueOf(rs.getString("category")),
            rs.getString("unit"),
            rs.getBigDecimal("low_stock_threshold"),
            rs.getString("supplier"),
            rs.getString("notes"),
            (UUID) rs.getObject("created_by"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            toInstant(rs.getTimestamp("deleted_at")),
            rs.getBigDecimal("current_stock")
    );

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public Optional<Input> findById(UUID id) {
        try {
            Input row = jdbc.queryForObject(
                    SELECT_WITH_STOCK + " WHERE i.id = ?",
                    inputRowMapper, id);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Devuelve true si el id pertenece a una fila viva (deleted_at IS NULL).
     * Usado por el gRPC server CheckInputExists.
     */
    public boolean existsByIdAndNotDeleted(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM input WHERE id = ? AND deleted_at IS NULL",
                Integer.class, id);
        return count != null && count > 0;
    }

    public boolean existsByNameAndCategoryAlive(String name, InputCategory category) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM input WHERE name = ? AND category = ?::input_category AND deleted_at IS NULL",
                Integer.class, name, category.name());
        return count != null && count > 0;
    }

    public boolean existsByNameAndCategoryAliveExcludingId(String name, InputCategory category, UUID excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM input WHERE name = ? AND category = ?::input_category " +
                        "AND deleted_at IS NULL AND id <> ?",
                Integer.class, name, category.name(), excludeId);
        return count != null && count > 0;
    }

    public UUID insert(String name, InputCategory category, String unit,
                       BigDecimal lowStockThreshold, String supplier, String notes,
                       UUID createdBy) {
        return jdbc.queryForObject("""
                        INSERT INTO input (name, category, unit, low_stock_threshold, supplier, notes, created_by)
                        VALUES (?, ?::input_category, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class,
                name, category.name(), unit, lowStockThreshold, supplier, notes, createdBy);
    }

    /**
     * UPDATE parcial (PATCH semantica). Cada parametro nullable se aplica solo
     * si no es null. Devuelve filas afectadas.
     */
    public int updatePartial(UUID id, String name, InputCategory category, String unit,
                             BigDecimal lowStockThreshold, String supplier, String notes,
                             boolean clearThreshold) {
        StringBuilder sql = new StringBuilder("UPDATE input SET updated_at = NOW()");
        List<Object> args = new ArrayList<>();
        if (name != null) {
            sql.append(", name = ?");
            args.add(name);
        }
        if (category != null) {
            sql.append(", category = ?::input_category");
            args.add(category.name());
        }
        if (unit != null) {
            sql.append(", unit = ?");
            args.add(unit);
        }
        if (lowStockThreshold != null) {
            sql.append(", low_stock_threshold = ?");
            args.add(lowStockThreshold);
        } else if (clearThreshold) {
            sql.append(", low_stock_threshold = NULL");
        }
        if (supplier != null) {
            sql.append(", supplier = ?");
            args.add(supplier);
        }
        if (notes != null) {
            sql.append(", notes = ?");
            args.add(notes);
        }
        sql.append(" WHERE id = ? AND deleted_at IS NULL");
        args.add(id);
        return jdbc.update(sql.toString(), args.toArray());
    }

    public int softDelete(UUID id) {
        return jdbc.update(
                "UPDATE input SET deleted_at = NOW(), updated_at = NOW() " +
                        "WHERE id = ? AND deleted_at IS NULL",
                id);
    }

    /**
     * Marca como deleted todos los inputs creados por el user.
     * Usado por {@link com.agro.inputservice.listener.UserDeletedListener}.
     */
    public int softDeleteByCreatedBy(UUID userId) {
        return jdbc.update(
                "UPDATE input SET deleted_at = NOW(), updated_at = NOW() " +
                        "WHERE created_by = ? AND deleted_at IS NULL",
                userId);
    }

    /**
     * Listado filtrado con paginacion en memoria sobre LIMIT/OFFSET.
     * Soporta: category, busqueda trigram por name (q), solo low_stock,
     * include_deleted.
     */
    public List<Input> search(InputCategory category, String q, boolean lowStockOnly,
                              boolean includeDeleted, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_STOCK);
        sql.append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!includeDeleted) {
            sql.append(" AND i.deleted_at IS NULL");
        }
        if (category != null) {
            sql.append(" AND i.category = ?::input_category");
            args.add(category.name());
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND i.name % ?");      // operador trigram similarity
            args.add(q);
        }
        if (lowStockOnly) {
            sql.append(" AND i.low_stock_threshold IS NOT NULL");
            sql.append(" AND COALESCE((SELECT SUM(CASE WHEN m.kind='IN' THEN m.quantity ELSE -m.quantity END)");
            sql.append(" FROM input_movement m WHERE m.input_id = i.id), 0) < i.low_stock_threshold");
        }
        sql.append(" ORDER BY i.name ASC OFFSET ? LIMIT ?");
        args.add(offset);
        args.add(limit);
        return jdbc.query(sql.toString(), inputRowMapper, args.toArray());
    }

    public long count(InputCategory category, String q, boolean lowStockOnly, boolean includeDeleted) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM input i WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!includeDeleted) {
            sql.append(" AND i.deleted_at IS NULL");
        }
        if (category != null) {
            sql.append(" AND i.category = ?::input_category");
            args.add(category.name());
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND i.name % ?");
            args.add(q);
        }
        if (lowStockOnly) {
            sql.append(" AND i.low_stock_threshold IS NOT NULL");
            sql.append(" AND COALESCE((SELECT SUM(CASE WHEN m.kind='IN' THEN m.quantity ELSE -m.quantity END)");
            sql.append(" FROM input_movement m WHERE m.input_id = i.id), 0) < i.low_stock_threshold");
        }
        Long c = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return c == null ? 0L : c;
    }

    public BigDecimal computeCurrentStock(UUID inputId) {
        BigDecimal v = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN kind='IN' THEN quantity ELSE -quantity END), 0) " +
                        "FROM input_movement WHERE input_id = ?",
                BigDecimal.class, inputId);
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Returns true si el input tiene algun movimiento (limita PATCH de
     * category — ver §6.3 del plan).
     */
    public boolean hasMovements(UUID inputId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM input_movement WHERE input_id = ?",
                Integer.class, inputId);
        return c != null && c > 0;
    }

    // ------------------------------------------------------------------
    //  stock_alert_log
    // ------------------------------------------------------------------

    public Optional<Map<String, Object>> findAlertLog(UUID inputId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT input_id, last_emitted_at, last_threshold, last_stock, is_currently_below " +
                            "FROM stock_alert_log WHERE input_id = ?",
                    inputId);
            return Optional.of(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void upsertAlertLog(UUID inputId, BigDecimal threshold, BigDecimal stock, boolean currentlyBelow) {
        jdbc.update("""
                INSERT INTO stock_alert_log (input_id, last_emitted_at, last_threshold, last_stock, is_currently_below)
                VALUES (?, NOW(), ?, ?, ?)
                ON CONFLICT (input_id) DO UPDATE SET
                  last_emitted_at = EXCLUDED.last_emitted_at,
                  last_threshold = EXCLUDED.last_threshold,
                  last_stock = EXCLUDED.last_stock,
                  is_currently_below = EXCLUDED.is_currently_below
                """, inputId, threshold, stock, currentlyBelow);
    }

    /**
     * Marca como "above" sin renovar last_emitted_at. Asi cuando vuelva a bajar
     * se podra reemitir inmediatamente (no necesita esperar 24h).
     */
    public void markAlertLogAsAbove(UUID inputId, BigDecimal threshold, BigDecimal stock) {
        jdbc.update("""
                INSERT INTO stock_alert_log (input_id, last_emitted_at, last_threshold, last_stock, is_currently_below)
                VALUES (?, NOW(), ?, ?, FALSE)
                ON CONFLICT (input_id) DO UPDATE SET
                  last_threshold = EXCLUDED.last_threshold,
                  last_stock = EXCLUDED.last_stock,
                  is_currently_below = FALSE
                """, inputId, threshold, stock);
    }
}
