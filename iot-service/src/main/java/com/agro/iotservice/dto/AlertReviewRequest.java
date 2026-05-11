package com.agro.iotservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of POST /alert/{id}/review. The reviewer identity comes from the
 * gateway-propagated X-User-Id header, not the body.
 */
public record AlertReviewRequest(
        @Size(max = 2000) String comment
) {
}
