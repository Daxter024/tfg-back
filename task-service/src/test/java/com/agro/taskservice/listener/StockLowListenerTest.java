package com.agro.taskservice.listener;

import com.agro.taskservice.event.StockLowEvent;
import com.agro.taskservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockLowListenerTest {

    @Mock NotificationService notificationService;
    @InjectMocks StockLowListener listener;

    @Test
    void onStockLow_delegatesToService() {
        UUID owner = UUID.randomUUID();
        UUID input = UUID.randomUUID();
        StockLowEvent ev = new StockLowEvent(input, "Glyphosate",
                new BigDecimal("3"), new BigDecimal("10"), "L", owner);

        listener.onStockLow(ev);

        verify(notificationService, times(1)).createFromStockLow(
                owner, input, "Glyphosate",
                new BigDecimal("3"), new BigDecimal("10"), "L");
    }
}
