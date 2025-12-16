package com.agro.seasonservice.grpc;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
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
            // Log error, maybe fallback or throw exception
            // For now, if we can't verify, we might assume it doesn't exist or rethrow
            throw new RuntimeException("Failed to verify terrain existence", e);
        }
    }
}
