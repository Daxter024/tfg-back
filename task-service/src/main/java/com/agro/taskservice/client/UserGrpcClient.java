package com.agro.taskservice.client;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import user.UserValidationRequest;
import user.UserValidationResponse;
import user.UserValidationServiceGrpc;

import java.util.UUID;

/**
 * Cliente gRPC contra auth-service:9091. Patron identico al
 * UserGrpcClient de terrain-service.
 */
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
