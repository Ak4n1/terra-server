package com.terra.api.realtime.infrastructure.persistence;

import com.terra.api.realtime.domain.model.RealtimeSession;
import com.terra.api.realtime.domain.model.RealtimeSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RealtimeSessionRepository extends JpaRepository<RealtimeSession, Long> {
    Optional<RealtimeSession> findByRealtimeSessionId(String realtimeSessionId);

    List<RealtimeSession> findByAccount_IdAndStatus(Long accountId, RealtimeSessionStatus status);

    List<RealtimeSession> findByAccountSession_IdAndStatus(Long accountSessionId, RealtimeSessionStatus status);
}
