package com.byawasaya.platform.catalog;

import java.util.List; 
import java.util.Optional; 
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,UUID> {
 List<Product> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);
 List<Product> findByBusinessIdAndActiveTrueOrderByCreatedAtDesc(UUID businessId);
 Optional<Product> findByIdAndBusinessId(UUID id, UUID businessId);
 Optional<Product> findByBusinessIdAndSlugAndActiveTrue(UUID businessId, String slug);
 long countByBusinessId(UUID businessId);
}
