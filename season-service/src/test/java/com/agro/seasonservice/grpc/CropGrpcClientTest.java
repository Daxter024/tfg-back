package com.agro.seasonservice.grpc;

import com.agro.crop.grpc.CropExistsResponse;
import com.agro.crop.grpc.CropIdRequest;
import com.agro.crop.grpc.CropServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropGrpcClientTest {

    @Mock
    private CropServiceGrpc.CropServiceBlockingStub stub;

    private CropGrpcClient client;

    @BeforeEach
    void setup() {
        client = new CropGrpcClient();
        ReflectionTestUtils.setField(client, "cropServiceStub", stub);
    }

    @Test
    @DisplayName("SEASON-5.06: checkCropExists happy path")
    void checkCropExists_returnsTrue() {
        UUID id = UUID.randomUUID();
        when(stub.checkCropExists(any(CropIdRequest.class)))
                .thenReturn(CropExistsResponse.newBuilder().setExists(true).build());

        assertThat(client.checkCropExists(id)).isTrue();
    }

    @Test
    @DisplayName("SEASON-5.07: checkCropExists exists=false")
    void checkCropExists_returnsFalse() {
        UUID id = UUID.randomUUID();
        when(stub.checkCropExists(any(CropIdRequest.class)))
                .thenReturn(CropExistsResponse.newBuilder().setExists(false).build());

        assertThat(client.checkCropExists(id)).isFalse();
    }

    @Test
    @DisplayName("SEASON-5.08: checkCropExists UNAVAILABLE → RuntimeException")
    void checkCropExists_grpcUnavailable_wrapsInRuntimeException() {
        UUID id = UUID.randomUUID();
        when(stub.checkCropExists(any(CropIdRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> client.checkCropExists(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to verify crop existence");
    }
}
