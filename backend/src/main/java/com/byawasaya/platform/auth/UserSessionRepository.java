package com.byawasaya.platform.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    List<UserSession> findAllByActiveAccountKey(String activeAccountKey);
    java.util.List<UserSession> findAllByBusinessIdAndActiveAccountKeyIsNotNull(java.util.UUID businessId);
}
