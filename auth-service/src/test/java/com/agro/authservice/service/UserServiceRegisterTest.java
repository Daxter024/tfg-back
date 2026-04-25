package com.agro.authservice.service;

import com.agro.authservice.dto.RegisterRequestDTO;
import com.agro.authservice.exception.EmailAlreadyExistsException;
import com.agro.authservice.exception.PasswordMismatchException;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRegisterTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private MailService mailService;
    @Mock private I18nService i18nService;

    @InjectMocks private UserService userService;

    private Role agricultorRole;

    @BeforeEach
    void setUp() {
        agricultorRole = new Role();
        agricultorRole.setId(3);
        agricultorRole.setName("agricultor");
    }

    @Test
    void register_ok_persistsUserAuditsAndSendsWelcomeMail() {
        RegisterRequestDTO dto = new RegisterRequestDTO(
                "Maria Lopez", "Maria@Example.COM", "Abcdefg1", "Abcdefg1");

        when(userRepository.existsByEmailIgnoreCase("maria@example.com")).thenReturn(false);
        when(roleRepository.findByName("agricultor")).thenReturn(Optional.of(agricultorRole));
        when(passwordEncoder.encode("Abcdefg1")).thenReturn("BCRYPT_HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(i18nService.getMessage("user.welcome.subject")).thenReturn("Bienvenido");
        when(i18nService.getMessage(eq("user.welcome.body"), any())).thenReturn("Hola Maria Lopez");

        UUID id = userService.register(dto);

        assertThat(id).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("maria@example.com");
        assertThat(saved.getFull_name()).isEqualTo("Maria Lopez");
        assertThat(saved.getPassword()).isEqualTo("BCRYPT_HASH");
        assertThat(saved.getRole_id()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getCreated_at()).isNotNull();
        assertThat(saved.getFailed_login_attempts()).isZero();

        verify(auditLogService).log(eq("USER_CREATED"), eq(null), eq(saved.getId()),
                eq(null), any(Map.class), eq(null));
        verify(mailService).send(eq("maria@example.com"), anyString(), anyString());
    }

    @Test
    void register_emailAlreadyExists_caseInsensitive_throws409() {
        RegisterRequestDTO dto = new RegisterRequestDTO(
                "Juan", "JUAN@example.com", "Abcdefg1", "Abcdefg1");

        when(userRepository.existsByEmailIgnoreCase("juan@example.com")).thenReturn(true);
        when(i18nService.getMessage("user.email.exists")).thenReturn("ya existe");

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ya existe");

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService, never()).log(anyString(), any(), any(), any(), any(), any());
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void register_passwordMismatch_throws400_withoutTouchingDb() {
        RegisterRequestDTO dto = new RegisterRequestDTO(
                "Pedro", "pedro@example.com", "Abcdefg1", "OtherPass1");
        when(i18nService.getMessage("user.validation.password.mismatch")).thenReturn("no coinciden");

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessageContaining("no coinciden");

        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_defaultRoleIsAgricultor() {
        RegisterRequestDTO dto = new RegisterRequestDTO(
                "Ana", "ana@example.com", "Abcdefg1", "Abcdefg1");

        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
        when(roleRepository.findByName("agricultor")).thenReturn(Optional.of(agricultorRole));
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.register(dto);

        verify(roleRepository, times(1)).findByName("agricultor");
    }
}
