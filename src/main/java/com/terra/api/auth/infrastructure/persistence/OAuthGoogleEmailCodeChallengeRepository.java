package com.terra.api.auth.infrastructure.persistence;

import com.terra.api.auth.domain.model.OAuthGoogleEmailCodeChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthGoogleEmailCodeChallengeRepository extends JpaRepository<OAuthGoogleEmailCodeChallenge, Long> {
    Optional<OAuthGoogleEmailCodeChallenge> findByChallengeId(String challengeId);

    Optional<OAuthGoogleEmailCodeChallenge> findByProviderSubject(String providerSubject);
}
