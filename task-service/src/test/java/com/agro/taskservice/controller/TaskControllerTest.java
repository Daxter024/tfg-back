package com.agro.taskservice.controller;

import com.agro.taskservice.dto.PageResponse;
import com.agro.taskservice.dto.TaskRequest;
import com.agro.taskservice.exception.GlobalExceptionHandler;
import com.agro.taskservice.exception.TaskDeleteConflictException;
import com.agro.taskservice.exception.TaskNotFoundException;
import com.agro.taskservice.exception.TerrainNotFoundException;
import com.agro.taskservice.repository.TaskRepository;
import com.agro.taskservice.service.I18nService;
import com.agro.taskservice.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    MockMvc mockMvc;
    TaskService taskService;
    I18nService i18nService;
    ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        taskService = Mockito.mock(TaskService.class);
        i18nService = Mockito.mock(I18nService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskController controller = new TaskController(taskService, i18nService);
        GlobalExceptionHandler advice = new GlobalExceptionHandler(i18nService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .build();
    }

    @Test
    void post_validBody_returns201() throws Exception {
        UUID createdId = UUID.randomUUID();
        when(taskService.createTask(any(TaskRequest.class), any(UUID.class))).thenReturn(createdId);

        TaskRequest req = new TaskRequest("IRRIGATION", UUID.randomUUID(),
                LocalDateTime.now().plusDays(1), 60, UUID.randomUUID(),
                null, null, null);

        mockMvc.perform(post("/task")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(createdId.toString()));
    }

    @Test
    void post_pastDate_returns400() throws Exception {
        // planned_at in the past triggers @Future
        TaskRequest req = new TaskRequest("IRRIGATION", UUID.randomUUID(),
                LocalDateTime.now().minusDays(1), 60, UUID.randomUUID(),
                null, null, null);

        mockMvc.perform(post("/task")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_terrainNotFound_returns404() throws Exception {
        when(taskService.createTask(any(), any())).thenThrow(new TerrainNotFoundException("missing"));

        TaskRequest req = new TaskRequest("IRRIGATION", UUID.randomUUID(),
                LocalDateTime.now().plusDays(1), 60, UUID.randomUUID(),
                null, null, null);

        mockMvc.perform(post("/task")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_byId_returnsProjection() throws Exception {
        UUID id = UUID.randomUUID();
        when(taskService.getTask(any(), any())).thenReturn(Map.of("id", id, "state", "PENDING"));

        mockMvc.perform(get("/task/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void get_list_returnsPageResponse() throws Exception {
        when(taskService.listTasks(any(TaskRepository.TaskFilters.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(get("/task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void delete_conflict_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TaskDeleteConflictException("history")).when(taskService).deleteTask(id);
        mockMvc.perform(delete("/task/{id}", id))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TaskNotFoundException("nope")).when(taskService).deleteTask(id);
        mockMvc.perform(delete("/task/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void calendar_invalidView_returns400() throws Exception {
        mockMvc.perform(get("/task/calendar")
                        .param("from", "2026-01-01T00:00:00")
                        .param("to", "2026-01-31T00:00:00")
                        .param("view", "INVALID"))
                .andExpect(status().isBadRequest());
    }
}
