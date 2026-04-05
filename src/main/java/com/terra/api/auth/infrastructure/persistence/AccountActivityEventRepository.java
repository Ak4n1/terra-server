package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.AccountActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountActivityEventRepository extends JpaRepository<AccountActivityEvent, Long> {

    Page<AccountActivityEvent> findByAccount_Id(Long accountId, Pageable pageable);
}
