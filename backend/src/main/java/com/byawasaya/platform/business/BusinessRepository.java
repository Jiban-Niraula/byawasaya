package com.byawasaya.platform.business;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    long countByStatus(BusinessStatus status);
    List<Business> findTop10ByOrderByCreatedAtDesc();
    List<Business> findAllByOrderByCreatedAtDesc();
    long countByCreatedAtAfter(Instant createdAt);
    Optional<Business> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsByTenantTagIgnoreCase(String tenantTag);
}
