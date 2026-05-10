package com.agro.seasonservice.grpc;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerrainGrpcClientTest {

    @Mock
    private TerrainServiceGrpc.TerrainServiceBlockingStub stub;

    private TerrainGrpcClient client;

    @BeforeEach
    void setup() {
        client = new TerrainGrpcClient();
        // The @GrpcClient field is normally injected; for unit tests we set it via reflection
        ReflectionTestUtils.setField(client, "terrainServiceStub", stub);
    }

    @Test
    @DisplayName("SEASON-5.01: checkTerrainExists happy path (exists=true)")
    void checkTerrainExists_returnsTrue() {
        UUID id = UUID.randomUUID();
        when(stub.checkTerrainExists(any(TerrainIdRequest.class)))
                .thenReturn(TerrainExistsResponse.newBuilder().setExists(true).build());

        boolean result = client.checkTerrainExists(id);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("SEASON-5.02: checkTerrainExists exists=false")
    void checkTerrainExists_returnsFalse() {
        UUID id = UUID.randomUUID();
        when(stub.checkTerrainExists(any(TerrainIdRequest.class)))
                .thenReturn(TerrainExistsResponse.newBuilder().setExists(false).build());

        boolean result = client.checkTerrainExists(id);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("SEASON-5.03: checkTerrainExists envuelve StatusRuntimeException(UNAVAILABLE) en RuntimeException")
    void checkTerrainExists_grpcUnavailable_wrapsInRuntimeException() {
        UUID id = UUID.randomUUID();
        when(stub.checkTerrainExists(any(TerrainIdRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> client.checkTerrainExists(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to verify terrain existence");
    }

    @Test
    @DisplayName("SEASON-5.04: checkTerrainExists con UUID nulo → RuntimeException (NPE atrapado por el wrapper)")
    void checkTerrainExists_nullUuid_wrapsNpe() {
        // El método tiene try { ... terrainId.toString() } catch (Exception)
        // → la NullPointerException se envuelve en RuntimeException.
        assertThatThrownBy(() -> client.checkTerrainExists(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to verify terrain existence")
                .hasCauseInstanceOf(NullPointerException.class);
    }
}
