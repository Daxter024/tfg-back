package com.agro.authservice.service;

import com.agro.authservice.dto.LoginRequestDTO;
import com.agro.authservice.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    public Optional<String> authenticate(LoginRequestDTO loginRequest) {
        Optional<String> token = userService.findByEmail(loginRequest.email())
                .filter(u -> passwordEncoder.matches(loginRequest.password(),
                        u.getPassword()))
                .map(u -> jwtUtil.generateToken(
                        u.getEmail(),
                        roleService.getRoleName(u.getRole_id())
                ));

        return token;
    }
}
