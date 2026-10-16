package com.byawasaya.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    Optional<UserAccount> findByPhone(String phone);
    boolean existsByUsernameIgnoreCase(String username);
    java.util.List<UserAccount> findAllByOrderByCreatedAtDesc();
    java.util.List<UserAccount> findAllByBusinessId(UUID businessId);
}
