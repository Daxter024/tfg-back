package com.agro.authservice.controller;

import com.agro.authservice.dto.ChangePasswordRequestDTO;
import com.agro.authservice.dto.ForgotPasswordRequestDTO;
import com.agro.authservice.dto.ResetPasswordRequestDTO;
import com.agro.authservice.exception.GlobalExceptionHandler;
import com.agro.authservice.exception.InvalidPasswordResetException;
import com.agro.authservice.service.AuthContextResolver;
import com.agro.authservice.service.I18nService;
import com.agro.authservice.service.PasswordService;
import com.agro.authservice.util.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class PasswordControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PasswordService passwordService;
    @MockitoBean private AuthContextResolver authContextResolver;
    @MockitoBean private I18nService i18nService;

    @Test
    void change_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authContextResolver.resolve(anyString()))
                .thenReturn(new AuthContext(userId, "a@b.c", "agricultor"));
        when(i18nService.getMessage("user.password.changed.ok")).thenReturn("Changed");

        ChangePasswordRequestDTO body = new ChangePasswordRequestDTO("Current1A", "New1Pass1", "New1Pass1");
        mockMvc.perform(post("/password/change")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changed"));

        verify(passwordService).change(any(), any(), any());
    }

    @Test
    void forgot_existingOrNot_alwaysReturns200WithSameMessage() throws Exception {
        when(i18nService.getMessage("user.password.forgot.sent")).thenReturn("If exists, sent");

        mockMvc.perform(post("/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequestDTO("ghost@x.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If exists, sent"));

        verify(passwordService).forgot(any(), any());
    }

    @Test
    void reset_invalidToken_returns400() throws Exception {
        doThrow(new InvalidPasswordResetException("nope"))
                .when(passwordService).reset(any(), any());
        when(i18nService.getMessage("user.password.reset.invalid.title")).thenReturn("Invalid");

        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("bad", "Abc12345", "Abc12345");
        mockMvc.perform(post("/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid"));
    }

    @Test
    void reset_validBody_returns200() throws Exception {
        when(i18nService.getMessage("user.password.reset.ok")).thenReturn("OK");

        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("token", "Abc12345", "Abc12345");
        mockMvc.perform(post("/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OK"));
    }

    @Test
    void change_invalidPayload_returns400_withoutCallingService() throws Exception {
        String invalid = """
                {"current_password":"","new_password":"weak","new_password_confirmation":""}
                """;
        mockMvc.perform(post("/password/change")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());

        verify(passwordService, never()).change(any(), any(), any());
    }
}
