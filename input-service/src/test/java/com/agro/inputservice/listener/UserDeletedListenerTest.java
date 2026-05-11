package com.agro.inputservice.listener;

import com.agro.inputservice.event.UserDeletedEvent;
import com.agro.inputservice.repository.InputRepository;
import com.agro.inputservice.repository.MovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletedListenerTest {

    @Mock MovementRepository movementRepository;
    @Mock InputRepository inputRepository;
    @InjectMocks UserDeletedListener listener;

    @Test
    void anonymizes_movements_and_soft_deletes_inputs() {
        UUID userId = UUID.randomUUID();
        when(movementRepository.anonymizePerformedBy(userId)).thenReturn(3);
        when(inputRepository.softDeleteByCreatedBy(userId)).thenReturn(2);

        listener.onUserDeleted(new UserDeletedEvent(userId));

        verify(movementRepository).anonymizePerformedBy(userId);
        verify(inputRepository).softDeleteByCreatedBy(userId);
    }

    @Test
    void no_failure_when_user_has_no_data() {
        UUID userId = UUID.randomUUID();
        when(movementRepository.anonymizePerformedBy(userId)).thenReturn(0);
        when(inputRepository.softDeleteByCreatedBy(userId)).thenReturn(0);

        listener.onUserDeleted(new UserDeletedEvent(userId));

        verify(movementRepository).anonymizePerformedBy(userId);
        verify(inputRepository).softDeleteByCreatedBy(userId);
    }
}
