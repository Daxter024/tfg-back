package com.agro.cropservice.service;

import com.agro.cropservice.constants.CropFieldConstants;
import com.agro.cropservice.repository.CropRepository;
import com.agro.cropservice.utils.FieldsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CropService {
    private final CropRepository cropRepository;
    private final FieldsValidator fieldsValidator = new FieldsValidator(CropFieldConstants.ALLOWED_FIELDS);

    public List<?> getCrops(String fields) {
        String selectedFields = fieldsValidator.validateAndProcess(fields);
        return cropRepository.findAllCrops(selectedFields);
    }

}
