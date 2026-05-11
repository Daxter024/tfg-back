package com.agro.taskservice.service;

import com.agro.taskservice.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class TaskExportServiceTest {

    @Mock TaskRepository taskRepository;

    @InjectMocks TaskExportService service;

    @Test
    void exportCsv_writesHeader_andRows() throws Exception {
        // Mock streamForExport to invoke RowCallbackHandler with 10_000 fake rows
        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            for (int i = 0; i < 10_000; i++) {
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                org.mockito.Mockito.when(rs.getObject("id")).thenReturn(UUID.randomUUID());
                org.mockito.Mockito.when(rs.getString("task_type_code")).thenReturn("IRRIGATION");
                org.mockito.Mockito.when(rs.getObject("terrain_id")).thenReturn(UUID.randomUUID());
                org.mockito.Mockito.when(rs.getTimestamp("planned_at"))
                        .thenReturn(Timestamp.valueOf("2026-05-01 10:00:00"));
                org.mockito.Mockito.when(rs.getString("state")).thenReturn("PENDING");
                org.mockito.Mockito.when(rs.getTimestamp("started_at")).thenReturn(null);
                org.mockito.Mockito.when(rs.getTimestamp("finished_at")).thenReturn(null);
                org.mockito.Mockito.when(rs.getObject("real_duration_minutes")).thenReturn(null);
                org.mockito.Mockito.when(rs.getObject("assigned_to")).thenReturn(UUID.randomUUID());
                org.mockito.Mockito.when(rs.getObject("created_by")).thenReturn(UUID.randomUUID());
                handler.processRow(rs);
            }
            return null;
        }).when(taskRepository).streamForExport(any(), any());

        StreamingResponseBody body = service.exportCsv(null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);
        String csv = baos.toString();

        String header = csv.substring(0, csv.indexOf('\n'));
        assertThat(header).isEqualTo("id,task_type_code,terrain_id,planned_at,state,started_at,finished_at,real_duration_minutes,assigned_to,created_by");
        // 1 header + 10_000 rows = 10_001 newlines
        long newlines = csv.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(10_001L);
    }

    @Test
    void csv_escapes_quotes_and_commas() throws Exception {
        // Two rows: one trivial, one with comma + quote in task_type_code (forced via mock)
        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            org.mockito.Mockito.when(rs.getObject("id")).thenReturn("00000000-0000-0000-0000-000000000001");
            org.mockito.Mockito.when(rs.getString("task_type_code")).thenReturn("FOO,BAR\"BAZ");
            org.mockito.Mockito.when(rs.getObject("terrain_id")).thenReturn(null);
            org.mockito.Mockito.when(rs.getTimestamp("planned_at")).thenReturn(null);
            org.mockito.Mockito.when(rs.getString("state")).thenReturn("PENDING");
            org.mockito.Mockito.when(rs.getTimestamp("started_at")).thenReturn(null);
            org.mockito.Mockito.when(rs.getTimestamp("finished_at")).thenReturn(null);
            org.mockito.Mockito.when(rs.getObject("real_duration_minutes")).thenReturn(null);
            org.mockito.Mockito.when(rs.getObject("assigned_to")).thenReturn(null);
            org.mockito.Mockito.when(rs.getObject("created_by")).thenReturn(null);
            handler.processRow(rs);
            return null;
        }).when(taskRepository).streamForExport(any(), any());

        StreamingResponseBody body = service.exportCsv(null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);
        String csv = baos.toString();
        assertThat(csv).contains("\"FOO,BAR\"\"BAZ\"");
    }
}
