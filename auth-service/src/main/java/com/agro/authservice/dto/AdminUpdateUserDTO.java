package com.agro.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserDTO(
        @NotBlank(message = "{user.validation.full_name.required}")
        @Size(min = 2, max = 120, message = "{user.validation.full_name.size}")
        String full_name,

        @NotBlank(message = "{user.validation.email.required}")
        @Email(message = "{user.validation.email.invalid}")
        String email,

        @NotBlank(message = "{user.validation.role.required}")
        String role,

        @NotBlank(message = "{user.validation.status.required}")
        @Pattern(regexp = "^(active|inactive)$", message = "{user.validation.status.invalid}")
        String status
) {
}
