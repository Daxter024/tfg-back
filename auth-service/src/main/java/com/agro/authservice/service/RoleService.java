package com.agro.authservice.service;

import com.agro.authservice.model.Role;
import com.agro.authservice.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /**
     * Lo utilizo solo para el nombre, aunque no es una buena práctica, cambiar la lógica
     * para que solo se use en JwtUtil para que si es null de el valor "user" allí
     */
    public String getRoleName(Integer roleId) {
        Optional<String> roleName = roleRepository.findById(roleId)
                .map(Role::getName);

        return roleName.orElse("user");
    }

}
