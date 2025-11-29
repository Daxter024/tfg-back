package com.agro.seasonservice.service;

import com.agro.seasonservice.constants.SeasonConstants;
import com.agro.seasonservice.repository.SeasonRepository;
import com.agro.seasonservice.utils.FieldsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeasonService {

    private final FieldsValidator fieldsValidator = new FieldsValidator(SeasonConstants.SEASON_ALLOWED_FIELDS);
    private final SeasonRepository seasonRepository;

    @Transactional(readOnly = true)
    public Object getSeason(UUID id, String fields) {
        String selectedFields = fieldsValidator.validateAndProcess(fields);
        return seasonRepository.getSeason(id, selectedFields);
    }
}
