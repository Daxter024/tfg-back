package com.agro.inputservice.grpc;

import com.agro.input.grpc.InputExistsResponse;
import com.agro.input.grpc.InputIdRequest;
import com.agro.inputservice.repository.InputRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InputGrpcServiceTest {

    @Mock InputRepository repository;
    @Mock StreamObserver<InputExistsResponse> observer;

    @InjectMocks InputGrpcService service;

    @Test
    void uuid_valid_and_exists_returns_true() {
        UUID id = UUID.randomUUID();
        when(repository.existsByIdAndNotDeleted(id)).thenReturn(true);

        service.checkInputExists(
                InputIdRequest.newBuilder().setInputId(id.toString()).build(),
                observer);

        ArgumentCaptor<InputExistsResponse> captor = ArgumentCaptor.forClass(InputExistsResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getExists()).isTrue();
        verify(observer).onCompleted();
    }

    @Test
    void uuid_valid_but_not_exists_returns_false() {
        UUID id = UUID.randomUUID();
        when(repository.existsByIdAndNotDeleted(id)).thenReturn(false);

        service.checkInputExists(
                InputIdRequest.newBuilder().setInputId(id.toString()).build(),
                observer);

        ArgumentCaptor<InputExistsResponse> captor = ArgumentCaptor.forClass(InputExistsResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getExists()).isFalse();
        verify(observer).onCompleted();
    }

    @Test
    void uuid_invalid_returns_false_without_error() {
        service.checkInputExists(
                InputIdRequest.newBuilder().setInputId("not-a-uuid").build(),
                observer);

        ArgumentCaptor<InputExistsResponse> captor = ArgumentCaptor.forClass(InputExistsResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getExists()).isFalse();
        verify(observer).onCompleted();
    }
}
