package com.agro.iotservice.client;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import user.UserValidationRequest;
import user.UserValidationResponse;
import user.UserValidationServiceGrpc;

import java.util.UUID;

/**
 * gRPC client to auth-service:9091 — used by ThresholdService to validate
 * every UUID in {@code notify_user_ids}.
 */
@Component
public class UserGrpcClient {

    @GrpcClient("auth-service")
    private UserValidationServiceGrpc.UserValidationServiceBlockingStub stub;

    public boolean validateUser(UUID userId) {
        try {
            UserValidationRequest request = UserValidationRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();
            UserValidationResponse response = stub.validateUser(request);
            return response.getExists();
        } catch (Exception e) {
            throw new RuntimeException("Could not validate user existence via gRPC", e);
        }
    }
}
