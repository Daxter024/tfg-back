package com.agro.authservice.controller;

import com.agro.authservice.dto.RegisterRequestDTO;
import com.agro.authservice.exception.EmailAlreadyExistsException;
import com.agro.authservice.exception.GlobalExceptionHandler;
import com.agro.authservice.service.AuthService;
import com.agro.authservice.service.I18nService;
import com.agro.authservice.service.UserService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class AuthControllerRegisterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private UserService userService;
    @MockitoBean private I18nService i18nService;

    @Test
    void register_returns201_withIdAndMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.register(any(RegisterRequestDTO.class))).thenReturn(userId);
        when(i18nService.getMessage("user.registered")).thenReturn("Usuario registrado correctamente");

        RegisterRequestDTO body = new RegisterRequestDTO(
                "Maria Lopez", "maria@example.com", "Abcdefg1", "Abcdefg1");

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.message").value("Usuario registrado correctamente"));
    }

    @Test
    void register_invalidPayload_returns400_withProblemDetail() throws Exception {
        when(i18nService.getMessage(any(String.class))).thenReturn("err");

        String invalid = """
                {"full_name":"","email":"not-an-email","password":"weak","password_confirmation":"weak"}
                """;

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());

        verify(userService, never()).register(any());
    }

    @Test
    void register_emailAlreadyExists_returns409() throws Exception {
        when(userService.register(any(RegisterRequestDTO.class)))
                .thenThrow(new EmailAlreadyExistsException("Ya existe"));
        when(i18nService.getMessage("user.email.exists.title")).thenReturn("Email ya registrado");

        RegisterRequestDTO body = new RegisterRequestDTO(
                "Maria", "maria@example.com", "Abcdefg1", "Abcdefg1");

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email ya registrado"));
    }
}
