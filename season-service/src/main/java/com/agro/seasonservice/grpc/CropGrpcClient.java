package com.agro.seasonservice.grpc;

import com.agro.crop.grpc.CropExistsResponse;
import com.agro.crop.grpc.CropIdRequest;
import com.agro.crop.grpc.CropServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CropGrpcClient {

    @GrpcClient("crop-service")
    private CropServiceGrpc.CropServiceBlockingStub cropServiceStub;

    public boolean checkCropExists(UUID cropId) {
        try {
            CropIdRequest request = CropIdRequest.newBuilder()
                    .setCropId(cropId.toString())
                    .build();

            CropExistsResponse response = cropServiceStub.checkCropExists(request);
            return response.getExists();
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify crop existence", e);
        }
    }
}
