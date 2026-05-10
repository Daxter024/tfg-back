package com.agro.cropservice.service;

import com.agro.cropservice.constants.CropFieldConstants;
import com.agro.cropservice.dto.CropCreatedResponse;
import com.agro.cropservice.dto.CropRequest;
import com.agro.cropservice.exception.CropTypeNotFoundException;
import com.agro.cropservice.model.CropType;
import com.agro.cropservice.repository.CropRepository;
import com.agro.cropservice.utils.FieldsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CropService {
    private final CropRepository cropRepository;
    private final FieldsValidator fieldsValidator = new FieldsValidator(CropFieldConstants.ALLOWED_FIELDS);
    private final I18nService i18nService;

    @Transactional(readOnly = true)
    public List<?> getCrops(String fields, Integer cropTypeId) {
        String selectedFields = fieldsValidator.validateAndProcess(fields);
        if (cropTypeId != null && !cropRepository.cropTypeExists(cropTypeId)) {
            throw new CropTypeNotFoundException(i18nService.getMessage("illegal.croptype.id"));
        }
        return cropRepository.findAllCrops(selectedFields, cropTypeId);
    }

    @Transactional(readOnly = true)
    public List<CropType> getCropTypes() {
        return cropRepository.findAllCropTypes();
    }

    @Transactional
    public CropCreatedResponse createCrop(CropRequest cropRequest) {
        if (!cropRepository.cropTypeExists(cropRequest.crop_type_id())) {
            throw new CropTypeNotFoundException(i18nService.getMessage("illegal.croptype.id"));
        }

        UUID id = cropRepository.insertCrop(
                cropRequest.name(),
                cropRequest.description(),
                cropRequest.crop_type_id()
        );

        return new CropCreatedResponse(
                id,
                cropRequest.name(),
                i18nService.getMessage("crop.created")
        );
    }

    @Transactional
    public void deleteCrop(UUID id) {
        cropRepository.deleteCrop(id);
    }
}
