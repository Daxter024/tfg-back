package com.agro.authservice.controller;

import com.agro.authservice.dto.ChangePasswordRequestDTO;
import com.agro.authservice.dto.ForgotPasswordRequestDTO;
import com.agro.authservice.dto.MessageResponseDTO;
import com.agro.authservice.dto.ResetPasswordRequestDTO;
import com.agro.authservice.service.AuthContextResolver;
import com.agro.authservice.service.I18nService;
import com.agro.authservice.service.PasswordService;
import com.agro.authservice.util.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/password")
public class PasswordController {

    private final PasswordService passwordService;
    private final AuthContextResolver authContextResolver;
    private final I18nService i18nService;

    public PasswordController(PasswordService passwordService,
                              AuthContextResolver authContextResolver,
                              I18nService i18nService) {
        this.passwordService = passwordService;
        this.authContextResolver = authContextResolver;
        this.i18nService = i18nService;
    }

    @Operation(summary = "Change password (authenticated)")
    @PostMapping("/change")
    public ResponseEntity<MessageResponseDTO> change(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody @Valid ChangePasswordRequestDTO body,
            HttpServletRequest request
    ) {
        AuthContext ctx = authContextResolver.resolve(authHeader);
        passwordService.change(ctx.userId(), body, extractIp(request));
        return ResponseEntity.ok(new MessageResponseDTO(i18nService.getMessage("user.password.changed.ok")));
    }

    @Operation(summary = "Request password reset link by email (always 200)")
    @PostMapping("/forgot")
    public ResponseEntity<MessageResponseDTO> forgot(
            @RequestBody @Valid ForgotPasswordRequestDTO body,
            HttpServletRequest request
    ) {
        passwordService.forgot(body, extractIp(request));
        return ResponseEntity.ok(new MessageResponseDTO(i18nService.getMessage("user.password.forgot.sent")));
    }

    @Operation(summary = "Reset password using a valid token")
    @PostMapping("/reset")
    public ResponseEntity<MessageResponseDTO> reset(
            @RequestBody @Valid ResetPasswordRequestDTO body,
            HttpServletRequest request
    ) {
        passwordService.reset(body, extractIp(request));
        return ResponseEntity.ok(new MessageResponseDTO(i18nService.getMessage("user.password.reset.ok")));
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
