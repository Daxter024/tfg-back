package com.agro.cropservice.service;

import com.agro.cropservice.dto.CropCreatedResponse;
import com.agro.cropservice.dto.CropRequest;
import com.agro.cropservice.exception.CropTypeNotFoundException;
import com.agro.cropservice.repository.CropRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropServiceTest {

    @Mock
    private CropRepository cropRepository;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private CropService cropService;

    @Test
    void getCrops_withoutFilter_callsRepoWithNullCropTypeId() {
        doReturn(List.of(Map.of("id", "x")))
                .when(cropRepository).findAllCrops(eq("*"), eq(null));

        List<?> result = cropService.getCrops(null, null);

        assertThat(result).hasSize(1);
        verify(cropRepository).findAllCrops("*", null);
        verify(cropRepository, never()).cropTypeExists(any());
    }

    @Test
    void getCrops_withCropTypeId_validatesExistenceAndForwardsFilter() {
        when(cropRepository.cropTypeExists(2)).thenReturn(true);
        doReturn(List.of(Map.of("id", "x"), Map.of("id", "y")))
                .when(cropRepository).findAllCrops(eq("*"), eq(2));

        List<?> result = cropService.getCrops(null, 2);

        assertThat(result).hasSize(2);
        verify(cropRepository).cropTypeExists(2);
        verify(cropRepository).findAllCrops("*", 2);
    }

    @Test
    void getCrops_withUnknownCropTypeId_throwsCropTypeNotFound() {
        when(cropRepository.cropTypeExists(99)).thenReturn(false);
        when(i18nService.getMessage("illegal.croptype.id")).thenReturn("crop type not found");

        assertThatThrownBy(() -> cropService.getCrops(null, 99))
                .isInstanceOf(CropTypeNotFoundException.class)
                .hasMessage("crop type not found");

        verify(cropRepository, never()).findAllCrops(any(), any());
    }

    @Test
    void getCrops_combinesFieldsWhitelistAndFilter() {
        when(cropRepository.cropTypeExists(1)).thenReturn(true);
        doReturn(List.of())
                .when(cropRepository).findAllCrops(any(String.class), eq(1));

        cropService.getCrops("id, name", 1);

        verify(cropRepository).findAllCrops(any(String.class), eq(1));
    }

    @Test
    void createCrop_happyPath_returnsResponseWithIdAndMessage() {
        UUID generated = UUID.randomUUID();
        when(cropRepository.cropTypeExists(1)).thenReturn(true);
        when(cropRepository.insertCrop("Trigo", "desc 10ch+", 1)).thenReturn(generated);
        when(i18nService.getMessage("crop.created")).thenReturn("Cultivo creado");

        CropCreatedResponse resp = cropService.createCrop(
                new CropRequest("Trigo", "desc 10ch+", 1));

        assertThat(resp.id()).isEqualTo(generated);
        assertThat(resp.name()).isEqualTo("Trigo");
        assertThat(resp.message()).isEqualTo("Cultivo creado");
    }

    @Test
    void createCrop_withUnknownCropTypeId_throwsCropTypeNotFound() {
        when(cropRepository.cropTypeExists(999)).thenReturn(false);
        when(i18nService.getMessage("illegal.croptype.id")).thenReturn("crop type not found");

        assertThatThrownBy(() -> cropService.createCrop(
                new CropRequest("X", "0123456789", 999)))
                .isInstanceOf(CropTypeNotFoundException.class);

        verify(cropRepository, never()).insertCrop(anyString(), anyString(), any());
    }

    @Test
    void deleteCrop_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        cropService.deleteCrop(id);

        verify(cropRepository).deleteCrop(id);
    }
}
