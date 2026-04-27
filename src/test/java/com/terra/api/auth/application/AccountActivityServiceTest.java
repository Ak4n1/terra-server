package com.terra.api.auth.application;

import com.terra.api.auth.domain.model.AccountActivityEvent;
import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.infrastructure.persistence.AccountActivityEventRepository;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.security.application.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivityServiceTest {

    @Test
    void shouldUseDescendingSortByDefault() {
        AccountActivityEventRepository accountActivityEventRepository = mock(AccountActivityEventRepository.class);
        AccountMasterRepository accountMasterRepository = mock(AccountMasterRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        AccountActivityService service = new AccountActivityService(
                accountActivityEventRepository,
                accountMasterRepository,
                new ObjectMapper(),
                clientIpResolver,
                requestProvider
        );

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setEmail("player@l2terra.online");

        when(accountMasterRepository.findByEmailIgnoreCase("player@l2terra.online")).thenReturn(Optional.of(account));
        when(accountActivityEventRepository.findByAccount_Id(eq(10L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<AccountActivityEvent>(List.of(), invocation.getArgument(1), 0));

        service.list(" Player@L2Terra.Online ", null, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(accountActivityEventRepository).findByAccount_Id(eq(10L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("occurredAt").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void shouldUseAscendingSortWhenRequested() {
        AccountActivityEventRepository accountActivityEventRepository = mock(AccountActivityEventRepository.class);
        AccountMasterRepository accountMasterRepository = mock(AccountMasterRepository.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        AccountActivityService service = new AccountActivityService(
                accountActivityEventRepository,
                accountMasterRepository,
                new ObjectMapper(),
                clientIpResolver,
                requestProvider
        );

        AccountMaster account = new AccountMaster();
        ReflectionTestUtils.setField(account, "id", 24L);
        account.setEmail("player@l2terra.online");

        when(accountMasterRepository.findByEmailIgnoreCase("player@l2terra.online")).thenReturn(Optional.of(account));
        when(accountActivityEventRepository.findByAccount_Id(eq(24L), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<AccountActivityEvent>(List.of(), invocation.getArgument(1), 0));

        service.list("player@l2terra.online", 2, 5, "asc", null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(accountActivityEventRepository).findByAccount_Id(eq(24L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("occurredAt").getDirection());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("id").getDirection());
    }
}
