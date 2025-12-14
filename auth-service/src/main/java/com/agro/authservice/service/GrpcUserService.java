package com.agro.authservice.service;

import com.agro.authservice.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import user.UserValidationRequest;
import user.UserValidationResponse;
import user.UserValidationServiceGrpc;

import java.util.UUID;

@GrpcService
public class GrpcUserService extends UserValidationServiceGrpc.UserValidationServiceImplBase {

    private final UserRepository userRepository;

    public GrpcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void validateUser(UserValidationRequest request, StreamObserver<UserValidationResponse> responseObserver) {
        boolean exists = false;
        try {
            UUID userId = UUID.fromString(request.getUserId());
            exists = userRepository.existsById(userId);
        } catch (IllegalArgumentException e) {
            // Invalid UUID
            exists = false;
        }

        UserValidationResponse response = UserValidationResponse.newBuilder()
                .setExists(exists)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
