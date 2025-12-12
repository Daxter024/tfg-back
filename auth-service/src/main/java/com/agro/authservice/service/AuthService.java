package com.agro.authservice.service;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.exception.EmailNotFoundException;
import com.agro.authservice.exception.InvalidCredentialsException;
import com.agro.authservice.model.User;
import com.agro.authservice.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final I18nService i18nService;

    public AuthService(UserService userService, RoleService roleService, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, I18nService i18nService) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.i18nService = i18nService;
    }

    // Aunque sea POST no está modificando nada en la bbdd solo lee datos
    @Transactional(readOnly = true)
    public String authenticate(LoginRequestDTO loginRequest) {
        User user = userService.findByEmail(loginRequest.email())
                .orElseThrow(() -> new EmailNotFoundException(i18nService.getMessage("auth.email.not.found")));

        if (!passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
            throw new InvalidCredentialsException(i18nService.getMessage("auth.invalid.credentials"));
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                roleService.getRoleName(user.getRole_id()));

        return token;
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        jwtUtil.validateToken(token);
        return true;
    }
}
