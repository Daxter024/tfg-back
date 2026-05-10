package com.agro.cropservice.controller;

import com.agro.cropservice.exception.GlobalExceptionHandler;
import com.agro.cropservice.service.CropService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CropControllerTest {

    @Mock
    private CropService cropService;

    @InjectMocks
    private CropController cropController;

    private MockMvc mockMvc;

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
}
