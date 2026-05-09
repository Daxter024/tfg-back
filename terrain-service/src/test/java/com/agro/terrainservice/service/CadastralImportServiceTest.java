package com.agro.terrainservice.service;

import com.agro.terrainservice.constants.ReferenceKind;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.exception.CadastralImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("TER-9.04 - Cadastral con 19 chars dispara 400 cadastral.reference.malformed")
    void fetch_throws400_whenCadastralReferenceTooShort() {
        CadastralImportRequest req = new CadastralImportRequest(
                "1234567890123456789", ReferenceKind.CADASTRAL  // 19 chars
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("cadastral.reference.malformed");
    }

    @Test
    @DisplayName("TER-9.05 - Cadastral con 21 chars dispara 400")
    void fetch_throws400_whenCadastralReferenceTooLong() {
        CadastralImportRequest req = new CadastralImportRequest(
                "123456789012345678901", ReferenceKind.CADASTRAL  // 21 chars
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("TER-9.06 - Cadastral con minusculas dispara 400")
    void fetch_throws400_whenCadastralReferenceLowercase() {
        CadastralImportRequest req = new CadastralImportRequest(
                "abcdefghijklmnopqrst", ReferenceKind.CADASTRAL  // lowercase
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("TER-9.07 - Cadastral con guiones dispara 400")
    void fetch_throws400_whenCadastralReferenceHasDashes() {
        CadastralImportRequest req = new CadastralImportRequest(
                "1234-ABCD-5678-EFGH", ReferenceKind.CADASTRAL
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("TER-9.08 - SIGPAC sin guiones dispara 400")
    void fetch_throws400_whenSigpacReferenceMalformed() {
        CadastralImportRequest req = new CadastralImportRequest(
                "13082010200100212", ReferenceKind.SIGPAC
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("TER-9.09 - SIGPAC con tramo extra dispara 400")
    void fetch_throws400_whenSigpacExtraSegment() {
        CadastralImportRequest req = new CadastralImportRequest(
                "13-082-01-02-001-002-1-9", ReferenceKind.SIGPAC
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("TER-9.10 - SIGPAC sintacticamente valido pasa formato pero falla por provider")
    void fetch_passesFormat_butFailsProvider_forValidSigpac() {
        // El formato sintactico pasa, pero el provider no esta configurado -> 502
        CadastralImportRequest req = new CadastralImportRequest(
                "13-082-1-1-1-1-1", ReferenceKind.SIGPAC
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getMessage()).contains("cadastral.api.unavailable");
    }

    @Test
    @DisplayName("TER-9.11 - cadastro.api.base-url vacia dispara 502 cadastral.api.unavailable")
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
    @DisplayName("TER-9.12 - sigpac.api.base-url vacia dispara 502")
    void fetch_throws502_whenSigpacBaseUrlNotConfigured() {
        CadastralImportRequest req = new CadastralImportRequest(
                "13-082-01-02-001-002-1", ReferenceKind.SIGPAC
        );

        CadastralImportException ex = assertThrows(CadastralImportException.class,
                () -> cadastralImportService.fetch(req));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getMessage()).contains("cadastral.api.unavailable");
    }
}
