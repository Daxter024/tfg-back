package com.agro.authservice.controller;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.dto.LoginResponseDTO;
import com.agro.authservice.dto.RefreshRequestDTO;
import com.agro.authservice.dto.RegisterRequestDTO;
import com.agro.authservice.dto.RegisterResponseDTO;
import com.agro.authservice.service.AuthService;
import com.agro.authservice.service.I18nService;
import com.agro.authservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
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
            @RequestBody @Valid LoginRequestDTO loginRequest,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(authService.authenticate(loginRequest, request));
    }

    @Operation(summary = "Register a new agricultor user")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(
            @RequestBody @Valid RegisterRequestDTO registerRequest
    ) {
        UUID userId = userService.register(registerRequest);
        RegisterResponseDTO body = new RegisterResponseDTO(
                userId,
                i18nService.getMessage("user.registered")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Rotate refresh token and emit new access token")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @RequestBody @Valid RefreshRequestDTO refreshRequest,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(authService.refresh(refreshRequest.refresh_token(), request));
    }

    @Operation(summary = "Logout: revoke current access jti and active refresh tokens")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.logout(authHeader.substring(7), request);
        return ResponseEntity.noContent().build();
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

}
