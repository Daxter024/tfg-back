package com.agro.terrainservice.service;

import com.agro.terrainservice.constants.ReferenceKind;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.exception.CadastralImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CadastralImportServiceTest {

    @Mock private I18nService i18nService;

    @InjectMocks private CadastralImportService cadastralImportService;

    @BeforeEach
    void setUp() {
        when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fetch_throws400_whenCadastralReferenceMalformed() {
        CadastralImportRequest req = new CadastralImportRequest(
                "TOO-SHORT", ReferenceKind.CADASTRAL
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("cadastral.reference.malformed");
    }

    @Test
    void fetch_throws400_whenSigpacReferenceMalformed() {
        CadastralImportRequest req = new CadastralImportRequest(
                "13-082", ReferenceKind.SIGPAC
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fetch_throws502_whenCadastralBaseUrlNotConfigured() {
        // Sin @Value se inyecta cadena vacia (default), por lo que la llamada
        // externa falla con 502 -> mensaje cadastral.api.unavailable.
        CadastralImportRequest req = new CadastralImportRequest(
                "1234ABCDEFGHIJKL5678", ReferenceKind.CADASTRAL
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getMessage()).contains("cadastral.api.unavailable");
    }

    @Test
    void fetch_throws502_whenSigpacBaseUrlNotConfigured() {
        CadastralImportRequest req = new CadastralImportRequest(
                "13-082-01-02-001-002-1", ReferenceKind.SIGPAC
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
