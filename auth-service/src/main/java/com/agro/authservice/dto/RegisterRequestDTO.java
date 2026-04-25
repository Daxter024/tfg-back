package com.agro.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "{user.validation.full_name.required}")
        @Size(min = 2, max = 120, message = "{user.validation.full_name.size}")
        String full_name,

        @NotBlank(message = "{user.validation.email.required}")
        @Email(message = "{user.validation.email.invalid}")
        String email,

        @NotBlank(message = "{user.validation.password.required}")
        @Size(min = 8, max = 128, message = "{user.validation.password.size}")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "{user.validation.password.weak}"
        )
        String password,

        @NotBlank(message = "{user.validation.password.confirmation.required}")
        String password_confirmation
) {
}
