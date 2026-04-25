package com.agro.terrainservice.service;

import com.agro.terrainservice.client.CadastroClient;
import com.agro.terrainservice.client.SigpacClient;
import com.agro.terrainservice.dto.CadastralImportRequest;
import com.agro.terrainservice.dto.CadastralImportResponse;
import com.agro.terrainservice.exception.CadastralReferenceMalformedException;
import com.agro.terrainservice.exception.CadastralReferenceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CadastralImportServiceTest {

    @Mock
    private CadastroClient cadastroClient;

    @Mock
    private SigpacClient sigpacClient;

    @Mock
    private I18nService i18nService;

    @InjectMocks
    private CadastralImportService cadastralImportService;

    @BeforeEach
    void setUp() {
        when(i18nService.getMessage(any(String.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage(any(String.class), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldReject_WhenCadastralRefIsMalformed() {
        CadastralImportRequest req = new CadastralImportRequest(
                "BAD-REF", CadastralImportRequest.ReferenceKind.CADASTRAL
        );
        assertThrows(CadastralReferenceMalformedException.class,
                () -> cadastralImportService.importReference(req));
        verify(cadastroClient, never()).fetch(any());
        verify(sigpacClient, never()).fetch(any());
    }

    @Test
    void shouldReject_WhenSigpacRefIsMalformed() {
        CadastralImportRequest req = new CadastralImportRequest(
                "01-002-03", CadastralImportRequest.ReferenceKind.SIGPAC
        );
        assertThrows(CadastralReferenceMalformedException.class,
                () -> cadastralImportService.importReference(req));
    }

    @Test
    void shouldCallCadastroClient_WhenKindIsCadastral() {
        CadastralImportRequest req = new CadastralImportRequest(
                "12345678901234567890", CadastralImportRequest.ReferenceKind.CADASTRAL
        );
        CadastralImportResponse stub = new CadastralImportResponse(
                "12345678901234567890", "Suggested", Map.of("type", "Polygon"),
                100d, "CULTIVO", "5", "Madrid", "Madrid"
        );
        when(cadastroClient.fetch("12345678901234567890")).thenReturn(Optional.of(stub));

        CadastralImportResponse out = cadastralImportService.importReference(req);

        assertEquals("Suggested", out.suggested_name());
    }

    @Test
    void shouldCallSigpacClient_WhenKindIsSigpac() {
        CadastralImportRequest req = new CadastralImportRequest(
                "01-002-03-04-005-006-7", CadastralImportRequest.ReferenceKind.SIGPAC
        );
        CadastralImportResponse stub = new CadastralImportResponse(
                "01-002-03-04-005-006-7", "Sigpac suggestion",
                Map.of("type", "Polygon"), 200d, "CEREAL", "3", "Madrid", "Madrid"
        );
        when(sigpacClient.fetch(any())).thenReturn(Optional.of(stub));

        CadastralImportResponse out = cadastralImportService.importReference(req);

        assertEquals("Sigpac suggestion", out.suggested_name());
        verify(cadastroClient, never()).fetch(any());
    }

    @Test
    void shouldThrowNotFound_WhenClientReturnsEmpty() {
        CadastralImportRequest req = new CadastralImportRequest(
                "12345678901234567890", CadastralImportRequest.ReferenceKind.CADASTRAL
        );
        when(cadastroClient.fetch(any())).thenReturn(Optional.empty());

        assertThrows(CadastralReferenceNotFoundException.class,
                () -> cadastralImportService.importReference(req));
    }
}
