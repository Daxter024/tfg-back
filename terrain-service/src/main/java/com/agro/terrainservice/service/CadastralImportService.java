package com.agro.terrainservice.service;

import com.agro.terrainservice.client.CadastroClient;
import com.agro.terrainservice.client.SigpacClient;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.dto.CadastralImportResponse;
import com.agro.terrainservice.exception.CadastralReferenceMalformedException;
import com.agro.terrainservice.exception.CadastralReferenceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CadastralImportService {

    /** Catastral: 20 caracteres alfanuméricos (mayúsculas o dígitos). */
    private static final Pattern CADASTRAL_PATTERN = Pattern.compile("^[0-9A-Z]{20}$");
    /** SIGPAC: provincia(2)-municipio(3)-agregado(2)-zona(2)-poligono(3)-parcela(3)-recinto(N). */
    private static final Pattern SIGPAC_PATTERN =
            Pattern.compile("^\\d{2}-\\d{3}-\\d{2}-\\d{2}-\\d{3}-\\d{3}-\\d+$");

    private final CadastroClient cadastroClient;
    private final SigpacClient sigpacClient;
    private final I18nService i18nService;

    public CadastralImportResponse importReference(CadastralImportRequest request) {
        validateSyntax(request);

        Optional<CadastralImportResponse> result = switch (request.kind()) {
            case CADASTRAL -> cadastroClient.fetch(request.reference());
            case SIGPAC -> sigpacClient.fetch(request.reference());
        };

        return result.orElseThrow(() -> new CadastralReferenceNotFoundException(
                i18nService.getMessage("cadastral.reference.not.found", request.reference())
        ));
    }

    private void validateSyntax(CadastralImportRequest request) {
        Pattern p = request.kind() == CadastralImportRequest.ReferenceKind.CADASTRAL
                ? CADASTRAL_PATTERN
                : SIGPAC_PATTERN;
        if (!p.matcher(request.reference()).matches()) {
            throw new CadastralReferenceMalformedException(
                    i18nService.getMessage("cadastral.reference.malformed")
            );
        }
    }
}
