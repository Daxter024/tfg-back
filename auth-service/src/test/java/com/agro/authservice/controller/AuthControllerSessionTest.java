package com.agro.authservice.controller;

import com.agro.authservice.dto.LoginResponseDTO;
import com.agro.authservice.dto.RefreshRequestDTO;
import com.agro.authservice.exception.GlobalExceptionHandler;
import com.agro.authservice.exception.InvalidRefreshTokenException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class AuthControllerSessionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private UserService userService;
    @MockitoBean private I18nService i18nService;

    @Test
    void refresh_ok_returnsNewPair() throws Exception {
        when(authService.refresh(eq("plain-token"), any()))
                .thenReturn(new LoginResponseDTO("new.access", "new.refresh", 3600));

        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequestDTO("plain-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new.access"))
                .andExpect(jsonPath("$.refresh_token").value("new.refresh"))
                .andExpect(jsonPath("$.expires_in_seconds").value(3600));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(anyString(), any()))
                .thenThrow(new InvalidRefreshTokenException("nope"));
        when(i18nService.getMessage("auth.refresh.invalid.title")).thenReturn("Invalid");

        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequestDTO("bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid"));
    }

    @Test
    void refresh_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(anyString(), any());
    }

    @Test
    void logout_withBearer_returns204() throws Exception {
        mockMvc.perform(post("/logout").header("Authorization", "Bearer abc"))
                .andExpect(status().isNoContent());

        verify(authService).logout(eq("abc"), any());
    }

    @Test
    void logout_withoutBearer_returns401() throws Exception {
        mockMvc.perform(post("/logout").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).logout(anyString(), any());
    }
}
