package com.byawasaya.platform.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id private UUID id;
    @Column(name = "business_id", nullable = false) private UUID businessId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(nullable = false, length = 140) private String label;
    @Column(length = 80) private String sku;
    @Column(name = "retail_price", precision = 12, scale = 2) private BigDecimal retailPrice;
    @Column(name = "wholesale_price", precision = 12, scale = 2) private BigDecimal wholesalePrice;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ProductVariant() {}

    public ProductVariant(UUID id, UUID businessId, UUID productId, String label, String sku,
            BigDecimal retailPrice, BigDecimal wholesalePrice, boolean active, int sortOrder) {
        this.id = id;
        this.businessId = businessId;
        this.productId = productId;
        this.label = label;
        this.sku = sku;
        this.retailPrice = retailPrice;
        this.wholesalePrice = wholesalePrice;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getProductId() { return productId; }
    public String getLabel() { return label; }
    public String getSku() { return sku; }
    public BigDecimal getRetailPrice() { return retailPrice; }
    public BigDecimal getWholesalePrice() { return wholesalePrice; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
