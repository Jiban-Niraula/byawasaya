package com.byawasaya.platform.onboarding;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TenantApplicationRepository extends JpaRepository<TenantApplication, UUID> {
 Optional<TenantApplication> findByOwnerAccountId(UUID ownerAccountId);
 Optional<TenantApplication> findByBusinessId(UUID businessId);
 Optional<TenantApplication> findByDraftTokenHash(String draftTokenHash);
 List<TenantApplication> findAllByOrderByCreatedAtDesc();
 List<TenantApplication> findByApplicationStatusOrderByCreatedAtAsc(String status);
}
