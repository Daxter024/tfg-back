package com.agro.authservice.service;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.exception.EmailNotFoundException;
import com.agro.authservice.exception.InvalidCredentialsException;
import com.agro.authservice.model.User;
import com.agro.authservice.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, RoleService roleService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public String authenticate(LoginRequestDTO loginRequest) {
        User user = userService.findByEmail(loginRequest.email())
                .orElseThrow(() -> new EmailNotFoundException("Email not found"));

        if (!passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
            throw new InvalidCredentialsException("Email or password are incorrect");
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                roleService.getRoleName(user.getRole_id())
        );

        return token;
    }

    public boolean validateToken(String token) {
        try {
            jwtUtil.validateToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
