package com.agro.taskservice.service;

import com.agro.taskservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * HU-TAR-03 — export CSV en streaming. Cada fila se serializa al
 * {@code OutputStream} a medida que la consume el JDBC, lo que permite
 * exportar miles de filas sin cargar toda la lista en memoria.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExportService {

    private static final String[] HEADERS = {
            "id", "task_type_code", "terrain_id", "planned_at", "state",
            "started_at", "finished_at", "real_duration_minutes",
            "assigned_to", "created_by"
    };

    private final TaskRepository taskRepository;

    public StreamingResponseBody exportCsv(TaskRepository.TaskFilters filters) {
        return out -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                writer.write(String.join(",", HEADERS));
                writer.newLine();
                taskRepository.streamForExport(filters, rs -> writeRow(writer, rs));
                writer.flush();
            } catch (IOException e) {
                log.warn("Failed to write CSV export", e);
                throw e;
            }
        };
    }

    private void writeRow(BufferedWriter writer, ResultSet rs) throws SQLException {
        try {
            writer.write(csv(rs.getObject("id")));
            writer.write(',');
            writer.write(csv(rs.getString("task_type_code")));
            writer.write(',');
            writer.write(csv(rs.getObject("terrain_id")));
            writer.write(',');
            writer.write(csv(ts(rs.getTimestamp("planned_at"))));
            writer.write(',');
            writer.write(csv(rs.getString("state")));
            writer.write(',');
            writer.write(csv(ts(rs.getTimestamp("started_at"))));
            writer.write(',');
            writer.write(csv(ts(rs.getTimestamp("finished_at"))));
            writer.write(',');
            writer.write(csv(rs.getObject("real_duration_minutes")));
            writer.write(',');
            writer.write(csv(rs.getObject("assigned_to")));
            writer.write(',');
            writer.write(csv(rs.getObject("created_by")));
            writer.newLine();
        } catch (IOException ioe) {
            throw new SQLException(ioe);
        }
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String ts(Timestamp t) {
        return t == null ? "" : t.toLocalDateTime().toString();
    }
}
