package com.agro.terrainservice.grpc;

import com.agro.parcel.grpc.ParcelExistsResponse;
import com.agro.parcel.grpc.ParcelIdRequest;
import com.agro.parcel.grpc.ParcelServiceGrpc;
import com.agro.terrainservice.repository.ParcelRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class ParcelGrpcService extends ParcelServiceGrpc.ParcelServiceImplBase {

    private final ParcelRepository parcelRepository;

    @Override
    public void checkParcelExists(ParcelIdRequest request,
                                  StreamObserver<ParcelExistsResponse> responseObserver) {
        boolean exists;
        try {
            UUID id = UUID.fromString(request.getParcelId());
            exists = parcelRepository.existsById(id);
        } catch (IllegalArgumentException iae) {
            exists = false;
        }
        responseObserver.onNext(
                ParcelExistsResponse.newBuilder().setExists(exists).build()
        );
        responseObserver.onCompleted();
    }
}
