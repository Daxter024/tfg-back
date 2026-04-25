package com.agro.authservice.service;

import com.agro.authservice.dto.AdminCreateUserDTO;
import com.agro.authservice.dto.AdminUpdateUserDTO;
import com.agro.authservice.dto.UserDetailDTO;
import com.agro.authservice.event.UserDeletedEvent;
import com.agro.authservice.exception.EmailAlreadyExistsException;
import com.agro.authservice.exception.SelfModificationForbiddenException;
import com.agro.authservice.exception.UserNotFoundException;
import com.agro.authservice.kafka.EventPublisher;
import com.agro.authservice.model.Role;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.RoleRepository;
import com.agro.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private EventPublisher eventPublisher;
    @Mock private I18nService i18nService;

    @InjectMocks private UserAdminService service;

    private Role agricultor;
    private Role tecnico;

    @BeforeEach
    void setUp() {
        agricultor = role(3, "agricultor");
        tecnico = role(4, "tecnico");
    }

    private Role role(int id, String name) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        return r;
    }

    private User existingUser(UUID id, int roleId, String status) {
        User u = new User();
        u.setId(id);
        u.setEmail("user@example.com");
        u.setFull_name("Old Name");
        u.setRole_id(roleId);
        u.setStatus(status);
        u.setCreated_at(Instant.now());
        return u;
    }

    @Test
    void create_ok_emitsAuditAndPersists() {
        UUID actor = UUID.randomUUID();
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("tecnico")).thenReturn(Optional.of(tecnico));
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UUID id = service.create(
                new AdminCreateUserDTO("Maria", "NEW@example.com", "Abcdefg1", "tecnico"),
                actor, "1.2.3.4");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(captor.getValue().getRole_id()).isEqualTo(4);
        verify(auditLogService).log(eq("USER_CREATED"), eq(actor), eq(id), eq(null), any(), eq("1.2.3.4"));
    }

    @Test
    void create_emailExists_throws409() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);
        when(i18nService.getMessage("user.email.exists")).thenReturn("dup");

        assertThatThrownBy(() -> service.create(
                new AdminCreateUserDTO("X", "x@example.com", "Abcdefg1", "tecnico"),
                UUID.randomUUID(), null))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void update_changingOwnRole_throws403() {
        UUID admin = UUID.randomUUID();
        User self = existingUser(admin, agricultor.getId(), "active");
        when(userRepository.findById(admin)).thenReturn(Optional.of(self));
        when(roleRepository.findByName("tecnico")).thenReturn(Optional.of(tecnico));
        when(roleRepository.findById(agricultor.getId())).thenReturn(Optional.of(agricultor));
        when(i18nService.getMessage("user.self.forbidden")).thenReturn("nope");

        assertThatThrownBy(() -> service.update(
                admin,
                new AdminUpdateUserDTO("Old Name", "user@example.com", "tecnico", "active"),
                admin, null))
                .isInstanceOf(SelfModificationForbiddenException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void update_changingRoleOfOtherUser_revokesRefreshTokensAndAudits() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User other = existingUser(target, agricultor.getId(), "active");
        when(userRepository.findById(target)).thenReturn(Optional.of(other));
        when(roleRepository.findByName("tecnico")).thenReturn(Optional.of(tecnico));
        when(roleRepository.findById(agricultor.getId())).thenReturn(Optional.of(agricultor));
        when(roleRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserDetailDTO out = service.update(target,
                new AdminUpdateUserDTO("Old Name", "user@example.com", "tecnico", "active"),
                admin, "9.9.9.9");

        verify(refreshTokenService).revokeAllForUser(target);
        verify(auditLogService).log(eq("ROLE_CHANGED"), eq(admin), eq(target), any(), any(), eq("9.9.9.9"));
        assertThat(out.role()).isEqualTo("tecnico");
    }

    @Test
    void update_renamingOnly_doesNotRevokeRefreshAndAuditsAsUserUpdated() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User other = existingUser(target, agricultor.getId(), "active");
        when(userRepository.findById(target)).thenReturn(Optional.of(other));
        when(roleRepository.findByName("agricultor")).thenReturn(Optional.of(agricultor));
        when(roleRepository.findById(agricultor.getId())).thenReturn(Optional.of(agricultor));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.update(target,
                new AdminUpdateUserDTO("New Name", "user@example.com", "agricultor", "active"),
                admin, null);

        verify(refreshTokenService, never()).revokeAllForUser(any());
        verify(auditLogService).log(eq("USER_UPDATED"), eq(admin), eq(target), any(), any(), any());
    }

    @Test
    void deactivate_self_throws403() {
        UUID admin = UUID.randomUUID();
        when(i18nService.getMessage("user.self.forbidden")).thenReturn("nope");

        assertThatThrownBy(() -> service.deactivate(admin, admin, null))
                .isInstanceOf(SelfModificationForbiddenException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void deactivate_other_setsInactiveAndRevokesRefresh() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User u = existingUser(target, agricultor.getId(), "active");
        when(userRepository.findById(target)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.deactivate(target, admin, null);

        assertThat(u.getStatus()).isEqualTo("inactive");
        verify(refreshTokenService).revokeAllForUser(target);
        verify(auditLogService).log(eq("USER_DEACTIVATED"), eq(admin), eq(target), any(), any(), any());
    }

    @Test
    void delete_self_throws403() {
        UUID admin = UUID.randomUUID();
        when(i18nService.getMessage("user.self.forbidden")).thenReturn("nope");

        assertThatThrownBy(() -> service.delete(admin, admin, null))
                .isInstanceOf(SelfModificationForbiddenException.class);
    }

    @Test
    void delete_other_emitsKafkaEventAndAudits() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User u = existingUser(target, agricultor.getId(), "active");
        when(userRepository.findById(target)).thenReturn(Optional.of(u));

        service.delete(target, admin, "10.0.0.1");

        verify(userRepository).deleteById(target);
        verify(eventPublisher).publishUserDeleted(any(UserDeletedEvent.class));
        verify(auditLogService).log(eq("USER_DELETED"), eq(admin), eq(target), any(), eq(null), eq("10.0.0.1"));
    }

    @Test
    void get_unknownId_throws404() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(i18nService.getMessage("user.not.found")).thenReturn("nope");

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
