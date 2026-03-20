package com.terra.api.auth.infrastructure.config;

import com.terra.api.auth.domain.model.Role;
import com.terra.api.auth.domain.model.RoleName;
import com.terra.api.auth.infrastructure.persistence.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RoleInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public RoleInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        Arrays.stream(RoleName.values()).forEach(roleName ->
                roleRepository.findByName(roleName)
                        .orElseGet(() -> roleRepository.save(new Role(roleName))));
    }
}
