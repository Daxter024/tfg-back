package com.agro.authservice.controller;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.dto.LoginResponseDTO;
import com.agro.authservice.dto.RegisterRequestDTO;
import com.agro.authservice.dto.RegisterResponseDTO;
import com.agro.authservice.service.AuthService;
import com.agro.authservice.service.I18nService;
import com.agro.authservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final I18nService i18nService;

    public AuthController(AuthService authService, UserService userService, I18nService i18nService) {
        this.authService = authService;
        this.userService = userService;
        this.i18nService = i18nService;
    }

    @Operation(summary = "Generate token on user login")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @RequestBody
            @Valid LoginRequestDTO loginRequest
    ) {
        String token = authService.authenticate(loginRequest);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @Operation(summary = "Register a new agricultor user")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(
            @RequestBody
            @Valid RegisterRequestDTO registerRequest
    ) {
        UUID userId = userService.register(registerRequest);
        RegisterResponseDTO body = new RegisterResponseDTO(
                userId,
                i18nService.getMessage("user.registered")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Validate Token")
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(
            @RequestHeader("Authorization") String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return authService.validateToken(authHeader.substring(7))
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @Operation(summary = "Delete User")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id
    ) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
