package com.agro.taskservice.client;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainOwnershipRequest;
import com.agro.terrain.grpc.TerrainOwnershipResponse;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cliente gRPC contra terrain-service:9093 — expone:
 *
 * <ul>
 *   <li>{@link #checkTerrainExists(UUID)} — verificación de existencia (legacy,
 *       lo siguen usando flujos sin user context).</li>
 *   <li>{@link #checkTerrainOwnership(UUID, UUID)} — verificación de
 *       existencia + propiedad para flujos con user context (POST /task etc).</li>
 * </ul>
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

    public Ownership checkTerrainOwnership(UUID terrainId, UUID userId) {
        try {
            TerrainOwnershipRequest request = TerrainOwnershipRequest.newBuilder()
                    .setTerrainId(terrainId.toString())
                    .setUserId(userId.toString())
                    .build();
            TerrainOwnershipResponse response = terrainServiceStub.checkTerrainOwnership(request);
            return new Ownership(response.getExists(), response.getOwnedByUser());
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify terrain ownership via gRPC", e);
        }
    }

    /** Resultado de {@link #checkTerrainOwnership(UUID, UUID)}. */
    public record Ownership(boolean exists, boolean ownedByUser) {}
}
