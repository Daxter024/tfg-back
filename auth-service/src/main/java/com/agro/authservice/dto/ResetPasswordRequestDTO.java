package com.agro.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank(message = "{user.password.reset.token.required}")
        String token,

        @NotBlank(message = "{user.validation.password.required}")
        @Size(min = 8, max = 128, message = "{user.validation.password.size}")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "{user.validation.password.weak}"
        )
        String new_password,

        @NotBlank(message = "{user.validation.password.confirmation.required}")
        String new_password_confirmation
) {
}
