package com.byawasaya.platform.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(UUID businessId, UUID productId);
    void deleteByBusinessIdAndProductId(UUID businessId, UUID productId);
}
