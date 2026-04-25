package com.agro.authservice.controller;

import com.agro.authservice.dto.AdminUpdateUserDTO;
import com.agro.authservice.dto.UserDetailDTO;
import com.agro.authservice.exception.ForbiddenRoleException;
import com.agro.authservice.exception.GlobalExceptionHandler;
import com.agro.authservice.exception.SelfModificationForbiddenException;
import com.agro.authservice.service.AuthContextResolver;
import com.agro.authservice.service.I18nService;
import com.agro.authservice.service.UserAdminService;
import com.agro.authservice.util.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserAdminController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class UserAdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserAdminService userAdminService;
    @MockitoBean private AuthContextResolver authContextResolver;
    @MockitoBean private I18nService i18nService;

    @Test
    void list_withoutAdminRole_returns403() throws Exception {
        when(authContextResolver.resolveAdmin(anyString()))
                .thenThrow(new ForbiddenRoleException("nope"));
        when(i18nService.getMessage("user.admin.required.title")).thenReturn("Forbidden");

        mockMvc.perform(get("/users").header("Authorization", "Bearer t"))
                .andExpect(status().isForbidden());

        verify(userAdminService, never()).list(anyString(), anyString(), anyString(), any());
    }

    @Test
    void list_asAdmin_returnsPage() throws Exception {
        UUID admin = UUID.randomUUID();
        when(authContextResolver.resolveAdmin(anyString()))
                .thenReturn(new AuthContext(admin, "a@b.c", "administrador"));
        Page<?> page = new PageImpl<>(List.of());
        when(userAdminService.list(any(), any(), any(), any())).thenReturn((Page) page);

        mockMvc.perform(get("/users?role=tecnico&status=active").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void update_self_returns403FromService() throws Exception {
        UUID admin = UUID.randomUUID();
        when(authContextResolver.resolveAdmin(anyString()))
                .thenReturn(new AuthContext(admin, "a@b.c", "administrador"));
        when(userAdminService.update(eq(admin), any(), eq(admin), any()))
                .thenThrow(new SelfModificationForbiddenException("nope"));
        when(i18nService.getMessage("user.self.forbidden.title")).thenReturn("Self");

        AdminUpdateUserDTO body = new AdminUpdateUserDTO("Me", "me@x.com", "tecnico", "active");
        mockMvc.perform(put("/users/" + admin)
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Self"));
    }

    @Test
    void delete_asAdmin_returns204() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(authContextResolver.resolveAdmin(anyString()))
                .thenReturn(new AuthContext(admin, "a@b.c", "administrador"));

        mockMvc.perform(delete("/users/" + target).header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());

        verify(userAdminService).delete(eq(target), eq(admin), any());
    }

    @Test
    void deactivate_asAdmin_returns204() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(authContextResolver.resolveAdmin(anyString()))
                .thenReturn(new AuthContext(admin, "a@b.c", "administrador"));

        mockMvc.perform(post("/users/" + target + "/deactivate").header("Authorization", "Bearer t"))
                .andExpect(status().isNoContent());

        verify(userAdminService).deactivate(eq(target), eq(admin), any());
    }
}
