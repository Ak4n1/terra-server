package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.Role;
import com.terra.api.auth.domain.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
