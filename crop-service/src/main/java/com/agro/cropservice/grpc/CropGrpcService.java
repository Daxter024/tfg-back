package com.agro.cropservice.grpc;

import com.agro.crop.grpc.CropExistsResponse;
import com.agro.crop.grpc.CropIdRequest;
import com.agro.crop.grpc.CropServiceGrpc;
import com.agro.cropservice.repository.CropRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class CropGrpcService extends CropServiceGrpc.CropServiceImplBase {

    private final CropRepository cropRepository;

    @Override
    public void checkCropExists(CropIdRequest request, StreamObserver<CropExistsResponse> responseObserver) {
        boolean exists = false;
        try {
            UUID id = UUID.fromString(request.getCropId());
            exists = cropRepository.cropExists(id);
        } catch (IllegalArgumentException e) {
            exists = false;
        }

        CropExistsResponse response = CropExistsResponse.newBuilder()
                .setExists(exists)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
