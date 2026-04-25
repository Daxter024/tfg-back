package com.agro.authservice.service;

import com.agro.authservice.dto.AdminCreateUserDTO;
import com.agro.authservice.dto.AdminUpdateUserDTO;
import com.agro.authservice.dto.UserDetailDTO;
import com.agro.authservice.dto.UserSummaryDTO;
import com.agro.authservice.event.UserDeletedEvent;
import com.agro.authservice.exception.EmailAlreadyExistsException;
import com.agro.authservice.exception.InvalidRoleException;
import com.agro.authservice.exception.SelfModificationForbiddenException;
import com.agro.authservice.exception.UserNotFoundException;
import com.agro.authservice.kafka.EventPublisher;
import com.agro.authservice.model.Role;
import com.agro.authservice.model.User;
import com.agro.authservice.repository.RoleRepository;
import com.agro.authservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final EventPublisher eventPublisher;
    private final I18nService i18nService;

    public UserAdminService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder,
                            AuditLogService auditLogService,
                            RefreshTokenService refreshTokenService,
                            EventPublisher eventPublisher,
                            I18nService i18nService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
        this.i18nService = i18nService;
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryDTO> list(String q, String roleName, String status, Pageable pageable) {
        Integer roleId = null;
        if (roleName != null && !roleName.isBlank()) {
            roleId = roleRepository.findByName(roleName)
                    .map(Role::getId)
                    .orElse(-1);
        }
        Page<User> page = userRepository.search(blankToNull(q), roleId, blankToNull(status), pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public UserDetailDTO get(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(i18nService.getMessage("user.not.found")));
        return toDetail(user);
    }

    @Transactional
    public UUID create(AdminCreateUserDTO dto, UUID actorUserId, String ip) {
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(i18nService.getMessage("user.email.exists"));
        }
        Role role = resolveRole(dto.role());

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFull_name(dto.full_name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole_id(role.getId());
        user.setStatus("active");
        user.setCreated_at(Instant.now());
        user.setFailed_login_attempts(0);
        userRepository.save(user);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("email", user.getEmail());
        after.put("role", role.getName());
        auditLogService.log("USER_CREATED", actorUserId, user.getId(), null, after, ip);

        return user.getId();
    }

    @Transactional
    public UserDetailDTO update(UUID id, AdminUpdateUserDTO dto, UUID actorUserId, String ip) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(i18nService.getMessage("user.not.found")));

        Role newRole = resolveRole(dto.role());
        Role oldRole = roleRepository.findById(user.getRole_id())
                .orElseThrow(() -> new IllegalStateException("Stored role missing for user " + id));

        boolean roleChanged = newRole.getId() != oldRole.getId();
        if (roleChanged && actorUserId != null && actorUserId.equals(id)) {
            throw new SelfModificationForbiddenException(i18nService.getMessage("user.self.forbidden"));
        }

        boolean deactivating = "inactive".equals(dto.status()) && "active".equals(user.getStatus());
        if (deactivating && actorUserId != null && actorUserId.equals(id)) {
            throw new SelfModificationForbiddenException(i18nService.getMessage("user.self.forbidden"));
        }

        Map<String, Object> before = snapshot(user, oldRole.getName());

        String newEmail = dto.email().trim().toLowerCase(Locale.ROOT);
        if (!newEmail.equals(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new EmailAlreadyExistsException(i18nService.getMessage("user.email.exists"));
        }

        user.setFull_name(dto.full_name().trim());
        user.setEmail(newEmail);
        user.setRole_id(newRole.getId());
        user.setStatus(dto.status());
        userRepository.save(user);

        Map<String, Object> after = snapshot(user, newRole.getName());

        if (roleChanged) {
            refreshTokenService.revokeAllForUser(id);
            auditLogService.log("ROLE_CHANGED", actorUserId, id, before, after, ip);
        } else {
            auditLogService.log("USER_UPDATED", actorUserId, id, before, after, ip);
        }

        return toDetail(user);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorUserId, String ip) {
        if (actorUserId != null && actorUserId.equals(id)) {
            throw new SelfModificationForbiddenException(i18nService.getMessage("user.self.forbidden"));
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(i18nService.getMessage("user.not.found")));
        if ("inactive".equals(user.getStatus())) {
            return;
        }
        Map<String, Object> before = Map.of("status", user.getStatus());
        user.setStatus("inactive");
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(id);
        auditLogService.log("USER_DEACTIVATED", actorUserId, id, before, Map.of("status", "inactive"), ip);
    }

    @Transactional
    public void reactivate(UUID id, UUID actorUserId, String ip) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(i18nService.getMessage("user.not.found")));
        if ("active".equals(user.getStatus())) {
            return;
        }
        Map<String, Object> before = Map.of("status", user.getStatus());
        user.setStatus("active");
        user.setFailed_login_attempts(0);
        user.setLocked_until(null);
        userRepository.save(user);
        auditLogService.log("USER_REACTIVATED", actorUserId, id, before, Map.of("status", "active"), ip);
    }

    @Transactional
    public void delete(UUID id, UUID actorUserId, String ip) {
        if (actorUserId != null && actorUserId.equals(id)) {
            throw new SelfModificationForbiddenException(i18nService.getMessage("user.self.forbidden"));
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(i18nService.getMessage("user.not.found")));
        Map<String, Object> before = snapshot(user, null);
        userRepository.deleteById(id);
        eventPublisher.publishUserDeleted(new UserDeletedEvent(id));
        auditLogService.log("USER_DELETED", actorUserId, id, before, null, ip);
    }

    private Role resolveRole(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new InvalidRoleException(i18nService.getMessage("user.validation.role.invalid")));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Map<String, Object> snapshot(User user, String roleName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("full_name", user.getFull_name());
        m.put("email", user.getEmail());
        m.put("status", user.getStatus());
        if (roleName != null) {
            m.put("role", roleName);
        }
        return m;
    }

    private UserSummaryDTO toSummary(User u) {
        String role = roleRepository.findById(u.getRole_id()).map(Role::getName).orElse(null);
        return new UserSummaryDTO(
                u.getId(), u.getFull_name(), u.getEmail(), role,
                u.getStatus(), u.getCreated_at(), u.getLast_login_at());
    }

    private UserDetailDTO toDetail(User u) {
        String role = roleRepository.findById(u.getRole_id()).map(Role::getName).orElse(null);
        return new UserDetailDTO(
                u.getId(), u.getFull_name(), u.getEmail(), role, u.getStatus(),
                u.getCreated_at(), u.getLast_login_at(),
                u.getFailed_login_attempts(), u.getLocked_until());
    }
}
