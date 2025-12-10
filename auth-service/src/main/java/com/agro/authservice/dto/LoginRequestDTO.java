package com.agro.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank(message = "{auth.validation.email.required}")
        @Email(message = "{auth.validation.email.invalid}")
        String email,

        @NotBlank(message = "{auth.validation.password.required}")
        @Size(min = 8, message = "{auth.validation.password.min}")
        String password
) {
}
