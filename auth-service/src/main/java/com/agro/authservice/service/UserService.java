package com.agro.authservice.service;

import com.agro.authservice.dto.RegisterRequestDTO;
import com.agro.authservice.exception.EmailAlreadyExistsException;
import com.agro.authservice.exception.PasswordMismatchException;
import com.agro.authservice.model.Role;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.RoleRepository;
import com.agro.authservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final String DEFAULT_REGISTRATION_ROLE = "agricultor";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final I18nService i18nService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService,
                       MailService mailService,
                       I18nService i18nService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
        this.i18nService = i18nService;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public UUID register(RegisterRequestDTO dto) {
        if (!dto.password().equals(dto.password_confirmation())) {
            throw new PasswordMismatchException(i18nService.getMessage("user.validation.password.mismatch"));
        }

        String normalizedEmail = dto.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException(i18nService.getMessage("user.email.exists"));
        }

        Role role = roleRepository.findByName(DEFAULT_REGISTRATION_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role '" + DEFAULT_REGISTRATION_ROLE + "' not found in database"));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFull_name(dto.full_name().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole_id(role.getId());
        user.setStatus("active");
        user.setCreated_at(Instant.now());
        user.setFailed_login_attempts(0);

        User saved = userRepository.save(user);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("email", saved.getEmail());
        after.put("role", role.getName());
        auditLogService.log("USER_CREATED", null, saved.getId(), null, after, null);

        mailService.send(
                saved.getEmail(),
                i18nService.getMessage("user.welcome.subject"),
                i18nService.getMessage("user.welcome.body", saved.getFull_name())
        );

        return saved.getId();
    }

}
