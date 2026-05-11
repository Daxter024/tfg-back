package com.agro.terrainservice.grpc;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainOwnershipRequest;
import com.agro.terrain.grpc.TerrainOwnershipResponse;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import com.agro.terrainservice.repository.TerrainRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class TerrainGrpcService extends TerrainServiceGrpc.TerrainServiceImplBase {

    private final TerrainRepository terrainRepository;

    @Override
    public void checkTerrainExists(TerrainIdRequest request, StreamObserver<TerrainExistsResponse> responseObserver) {
        boolean exists = false;
        try {
            UUID id = UUID.fromString(request.getTerrainId());
            exists = terrainRepository.existsById(id);
        } catch (IllegalArgumentException e) {
            exists = false;
        }

        TerrainExistsResponse response = TerrainExistsResponse.newBuilder()
                .setExists(exists)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Resuelve existencia + propiedad del terreno en una sola RPC.
     *
     * <ul>
     *   <li>UUID malformado en cualquiera de los inputs → {@code exists=false, owned=false}.</li>
     *   <li>Terreno no existe → {@code exists=false, owned=false}.</li>
     *   <li>Terreno existe pero el {@code user_id} no coincide → {@code exists=true, owned=false}.</li>
     *   <li>Coincide → {@code exists=true, owned=true}.</li>
     * </ul>
     */
    @Override
    public void checkTerrainOwnership(TerrainOwnershipRequest request,
                                      StreamObserver<TerrainOwnershipResponse> responseObserver) {
        boolean exists = false;
        boolean owned = false;
        try {
            UUID terrainId = UUID.fromString(request.getTerrainId());
            UUID userId = UUID.fromString(request.getUserId());
            Optional<UUID> owner = terrainRepository.findOwnerById(terrainId);
            if (owner.isPresent()) {
                exists = true;
                owned = owner.get().equals(userId);
            }
        } catch (IllegalArgumentException e) {
            // UUID inválido → exists=false, owned=false
        }

        TerrainOwnershipResponse response = TerrainOwnershipResponse.newBuilder()
                .setExists(exists)
                .setOwnedByUser(owned)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
