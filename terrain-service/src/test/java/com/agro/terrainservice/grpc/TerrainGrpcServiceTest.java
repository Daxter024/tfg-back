package com.agro.terrainservice.grpc;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrainservice.repository.TerrainRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del servicio gRPC. No levantamos un servidor real (eso seria
 * integracion); aqui validamos la logica de la implementacion contra mocks.
 */
@ExtendWith(MockitoExtension.class)
class TerrainGrpcServiceTest {

    @Mock private TerrainRepository terrainRepository;
    @Mock @SuppressWarnings("unchecked") private StreamObserver<TerrainExistsResponse> responseObserver;

    @InjectMocks private TerrainGrpcService grpcService;

    @Test
    @DisplayName("TER-10.01 - terreno existente devuelve exists=true")
    void checkTerrainExists_returnsTrue_whenTerrainExists() {
        UUID id = UUID.randomUUID();
        when(terrainRepository.existsById(id)).thenReturn(true);

        TerrainIdRequest req = TerrainIdRequest.newBuilder().setTerrainId(id.toString()).build();
        grpcService.checkTerrainExists(req, responseObserver);

        ArgumentCaptor<TerrainExistsResponse> captor = ArgumentCaptor.forClass(TerrainExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        assertThat(captor.getValue().getExists()).isTrue();
    }

    @Test
    @DisplayName("TER-10.02 - terreno inexistente devuelve exists=false")
    void checkTerrainExists_returnsFalse_whenTerrainMissing() {
        UUID id = UUID.randomUUID();
        when(terrainRepository.existsById(id)).thenReturn(false);

        TerrainIdRequest req = TerrainIdRequest.newBuilder().setTerrainId(id.toString()).build();
        grpcService.checkTerrainExists(req, responseObserver);

        ArgumentCaptor<TerrainExistsResponse> captor = ArgumentCaptor.forClass(TerrainExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        assertThat(captor.getValue().getExists()).isFalse();
    }

    @Test
    @DisplayName("TER-10.03 - UUID malformado devuelve exists=false (sin error gRPC)")
    void checkTerrainExists_returnsFalse_whenUuidMalformed() {
        TerrainIdRequest req = TerrainIdRequest.newBuilder().setTerrainId("abc").build();
        grpcService.checkTerrainExists(req, responseObserver);

        ArgumentCaptor<TerrainExistsResponse> captor = ArgumentCaptor.forClass(TerrainExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        assertThat(captor.getValue().getExists()).isFalse();
    }

    @Test
    @DisplayName("TER-10.04 - cadena vacia devuelve exists=false")
    void checkTerrainExists_returnsFalse_whenEmptyString() {
        TerrainIdRequest req = TerrainIdRequest.newBuilder().setTerrainId("").build();
        grpcService.checkTerrainExists(req, responseObserver);

        ArgumentCaptor<TerrainExistsResponse> captor = ArgumentCaptor.forClass(TerrainExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        assertThat(captor.getValue().getExists()).isFalse();
    }

    @Test
    @DisplayName("TER-10.05 - 100 llamadas concurrentes devuelven exists=true sin error")
    void checkTerrainExists_concurrent100Calls() throws Exception {
        UUID id = UUID.randomUUID();
        when(terrainRepository.existsById(id)).thenReturn(true);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger trueCount = new AtomicInteger(0);

        try {
            CompletableFuture<?>[] futures = new CompletableFuture[100];
            for (int i = 0; i < 100; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    @SuppressWarnings("unchecked")
                    StreamObserver<TerrainExistsResponse> obs = org.mockito.Mockito.mock(StreamObserver.class);
                    TerrainIdRequest req = TerrainIdRequest.newBuilder()
                            .setTerrainId(id.toString()).build();
                    grpcService.checkTerrainExists(req, obs);
                    ArgumentCaptor<TerrainExistsResponse> cap =
                            ArgumentCaptor.forClass(TerrainExistsResponse.class);
                    verify(obs).onNext(cap.capture());
                    if (cap.getValue().getExists()) {
                        trueCount.incrementAndGet();
                    }
                }, pool);
            }
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        assertThat(trueCount.get()).isEqualTo(100);
    }
}
