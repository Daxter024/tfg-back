package com.agro.terrainservice.grpc;

import com.agro.terrain.grpc.TerrainExistsResponse;
import com.agro.terrain.grpc.TerrainIdRequest;
import com.agro.terrain.grpc.TerrainServiceGrpc;
import com.agro.terrainservice.repository.TerrainRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

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
}
