package com.agro.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(
        @NotBlank(message = "{user.validation.email.required}")
        @Email(message = "{user.validation.email.invalid}")
        String email
) {
}
