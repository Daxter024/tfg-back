package com.agro.iotservice.client;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * gRPC client to terrain-service:9093 — verifies that a terrain id exists at
 * sensor creation time. Read-only check, used as a fast pre-write guard. No
 * Caffeine cache in v1 (per plan §11.3); add later only if load tests show
 * pressure.
 */
@Component
public class TerrainGrpcClient {

    @GrpcClient("terrain-service")
    private TerrainServiceGrpc.TerrainServiceBlockingStub stub;

    public boolean checkTerrainExists(UUID terrainId) {
        try {
            TerrainIdRequest request = TerrainIdRequest.newBuilder()
                    .setTerrainId(terrainId.toString())
                    .build();
            TerrainExistsResponse response = stub.checkTerrainExists(request);
            return response.getExists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify terrain existence via gRPC", e);
        }
    }
}
