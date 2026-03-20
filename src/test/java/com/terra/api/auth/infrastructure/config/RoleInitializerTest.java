package com.terra.api.auth.infrastructure.config;

import com.terra.api.auth.domain.model.RoleName;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.auth.infrastructure.persistence.AccountSessionRepository;
import com.terra.api.auth.infrastructure.persistence.AccountVerificationRepository;
import com.terra.api.auth.infrastructure.persistence.RoleRepository;
import com.terra.api.notifications.infrastructure.persistence.AccountNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RoleInitializerTest {

    @Autowired
    private RoleInitializer roleInitializer;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccountSessionRepository accountSessionRepository;

    @Autowired
    private AccountMasterRepository accountMasterRepository;

    @Autowired
    private AccountVerificationRepository accountVerificationRepository;

    @Autowired
    private AccountNotificationRepository accountNotificationRepository;

    @BeforeEach
    void setUp() {
        accountNotificationRepository.deleteAll();
        accountSessionRepository.deleteAll();
        accountVerificationRepository.deleteAll();
        accountMasterRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    void shouldSeedAllRolesWhenMissing() throws Exception {
        roleInitializer.run();

        assertEquals(RoleName.values().length, roleRepository.count());
        for (RoleName roleName : RoleName.values()) {
            assertTrue(roleRepository.findByName(roleName).isPresent());
        }
    }

    @Test
    void shouldBeIdempotentWhenRolesAlreadyExist() throws Exception {
        roleInitializer.run();
        roleInitializer.run();

        assertEquals(RoleName.values().length, roleRepository.count());
    }
}
