package com.agro.authservice.event;

import java.util.UUID;

public record UserDeletedEvent(UUID userId) {
}
