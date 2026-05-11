package com.agro.taskservice.client;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cliente gRPC contra terrain-service:9093 — solo expone
 * CheckTerrainExists. Patron identico al de season-service.
 */
@Component
public class TerrainGrpcClient {

    @GrpcClient("terrain-service")
    private TerrainServiceGrpc.TerrainServiceBlockingStub terrainServiceStub;

    public boolean checkTerrainExists(UUID terrainId) {
        try {
            TerrainIdRequest request = TerrainIdRequest.newBuilder()
                    .setTerrainId(terrainId.toString())
                    .build();
            TerrainExistsResponse response = terrainServiceStub.checkTerrainExists(request);
            return response.getExists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify terrain existence via gRPC", e);
        }
    }
}
