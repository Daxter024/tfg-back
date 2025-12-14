package com.agro.terrainservice.client;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import user.UserValidationRequest;
import user.UserValidationResponse;
import user.UserValidationServiceGrpc;

import java.util.UUID;

@Component
public class UserGrpcClient {

    @GrpcClient("auth-service")
    private UserValidationServiceGrpc.UserValidationServiceBlockingStub userValidationStub;

    public boolean validateUser(UUID userId) {
        try {
            UserValidationRequest request = UserValidationRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();
            UserValidationResponse response = userValidationStub.validateUser(request);
            return response.getExists();
        } catch (Exception e) {
            throw new RuntimeException("Could not validate user existence via gRPC", e);
        }
    }
}
