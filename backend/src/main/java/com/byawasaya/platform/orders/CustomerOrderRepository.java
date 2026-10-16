package com.byawasaya.platform.orders;
import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder,UUID> {
 List<CustomerOrder> findTop10ByOrderByCreatedAtDesc();
 List<CustomerOrder> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);
 java.util.Optional<CustomerOrder> findByIdAndBusinessId(UUID id,UUID businessId);
 java.util.Optional<CustomerOrder> findByBusinessIdAndIdempotencyKey(UUID businessId,String idempotencyKey);
 long countByBusinessId(UUID businessId);
 long countByBusinessIdAndStatus(UUID businessId, OrderStatus status);
 long countByStatus(OrderStatus status);
}
