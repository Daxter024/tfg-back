package com.agro.cropservice.controller;

import com.agro.cropservice.dto.CropCreatedResponse;
import com.agro.cropservice.exception.CropNotFoundException;
import com.agro.cropservice.exception.GlobalExceptionHandler;
import com.agro.cropservice.service.CropService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CropControllerTest {

    @Mock
    private CropService cropService;

    @InjectMocks
    private CropController cropController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(cropController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCrops_withoutFilter_passesNullCropTypeId() throws Exception {
        doReturn(List.of(Map.of("id", "a")))
                .when(cropService).getCrops(isNull(), isNull());

        mockMvc.perform(get("/crop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(cropService).getCrops(null, null);
    }

    @Test
    void getCrops_withCropTypeId_bindsAsInteger() throws Exception {
        doReturn(List.of(Map.of("id", "a"), Map.of("id", "b")))
                .when(cropService).getCrops(isNull(), eq(3));

        mockMvc.perform(get("/crop").param("crop_type_id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(cropService).getCrops(null, 3);
    }

    @Test
    void getCrops_withFieldsAndCropTypeId_bindsBoth() throws Exception {
        doReturn(List.of())
                .when(cropService).getCrops(eq("id,name"), eq(1));

        mockMvc.perform(get("/crop")
                        .param("fields", "id,name")
                        .param("crop_type_id", "1"))
                .andExpect(status().isOk());

        verify(cropService).getCrops("id,name", 1);
    }

    @Test
    void getCrops_withNonNumericCropTypeId_returns400() throws Exception {
        mockMvc.perform(get("/crop").param("crop_type_id", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCrop_returns201WithIdNameAndMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(cropService.createCrop(any())).thenReturn(
                new CropCreatedResponse(id, "Trigo", "Cultivo creado"));

        String body = """
                {"name":"Trigo","description":"desc al menos 10","crop_type_id":1}
                """;

        mockMvc.perform(post("/crop").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Trigo"))
                .andExpect(jsonPath("$.message").value("Cultivo creado"));
    }

    @Test
    void createCrop_withInvalidBody_returns400WithErrors() throws Exception {
        // body vacío → 3 errores de validación, sin llegar al service
        mockMvc.perform(post("/crop").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void deleteCrop_existingId_returns204NoBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/crop/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cropService).deleteCrop(id);
    }

    @Test
    void deleteCrop_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new CropNotFoundException("crop with id " + id + " not found"))
                .when(cropService).deleteCrop(id);

        mockMvc.perform(delete("/crop/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Crop not found"))
                .andExpect(jsonPath("$.detail").value("crop with id " + id + " not found"));
    }
}
