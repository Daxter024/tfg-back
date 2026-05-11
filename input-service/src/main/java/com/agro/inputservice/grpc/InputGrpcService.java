package com.agro.inputservice.grpc;

import com.agro.input.grpc.InputExistsResponse;
import com.agro.input.grpc.InputIdRequest;
import com.agro.input.grpc.InputServiceGrpc;
import com.agro.inputservice.repository.InputRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * Servidor gRPC {@code CheckInputExists}. Devuelve {@code true} solo si el
 * input existe y no esta soft-deleted — task-service usa esto para validar
 * planned_inputs[].input_id antes de insertar una tarea.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class InputGrpcService extends InputServiceGrpc.InputServiceImplBase {

    private final InputRepository repository;

    @Override
    public void checkInputExists(InputIdRequest request, StreamObserver<InputExistsResponse> responseObserver) {
        boolean exists = false;
        try {
            UUID id = UUID.fromString(request.getInputId());
            exists = repository.existsByIdAndNotDeleted(id);
        } catch (IllegalArgumentException ignored) {
            // UUID invalido -> false (no error). El cliente decide si trata
            // esto como bad-request en su capa de presentacion.
            log.debug("CheckInputExists with non-UUID input_id='{}'", request.getInputId());
        }
        responseObserver.onNext(InputExistsResponse.newBuilder().setExists(exists).build());
        responseObserver.onCompleted();
    }
}
