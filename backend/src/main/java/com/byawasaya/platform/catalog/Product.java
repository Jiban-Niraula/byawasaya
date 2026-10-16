package com.byawasaya.platform.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity @Table(name="products", uniqueConstraints=@UniqueConstraint(name="uk_product_store_slug", columnNames={"business_id","slug"}))
public class Product {
    @Id @GeneratedValue private UUID id;
    @Column(name="business_id", nullable=false) private UUID businessId;
    @Column(nullable=false, length=140) private String name;
    @Column(nullable=false, length=160) private String slug;
    @Column(length=80) private String sku;
    @Column(nullable=false, length=80) private String category;
    @Column(length=3000) private String description;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal retailPrice;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal wholesalePrice;
    @Column(nullable=false) private int wholesaleMinQuantity = 5;
    @Column(length=1000) private String imageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private PaymentPolicy paymentPolicy = PaymentPolicy.COD_ALLOWED;
    @Column(nullable=false) private int advancePaymentPercent = 30;
    @Column(nullable=false) private boolean active = true;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    protected Product() {}
    public Product(UUID businessId, String name, String slug, String sku, String category, String description,
            BigDecimal retailPrice, BigDecimal wholesalePrice, int wholesaleMinQuantity, String imageUrl,
            PaymentPolicy paymentPolicy, int advancePaymentPercent) {
        this.businessId=businessId; this.name=name; this.slug=slug; this.sku=sku; this.category=category;
        this.description=description; this.retailPrice=retailPrice; this.wholesalePrice=wholesalePrice;
        this.wholesaleMinQuantity=wholesaleMinQuantity; this.imageUrl=imageUrl; this.paymentPolicy=paymentPolicy;
        this.advancePaymentPercent=advancePaymentPercent;
    }
    @PrePersist void created() { if(createdAt==null) createdAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getBusinessId(){return businessId;} public String getName(){return name;}
    public String getSlug(){return slug;} public String getSku(){return sku;} public String getCategory(){return category;}
    public String getDescription(){return description;} public BigDecimal getRetailPrice(){return retailPrice;}
    public BigDecimal getWholesalePrice(){return wholesalePrice;} public int getWholesaleMinQuantity(){return wholesaleMinQuantity;}
    public String getImageUrl(){return imageUrl;} public PaymentPolicy getPaymentPolicy(){return paymentPolicy;}
    public int getAdvancePaymentPercent(){return advancePaymentPercent;} public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;}
    public void update(String name,String slug,String sku,String category,String description,BigDecimal retailPrice,
            BigDecimal wholesalePrice,int wholesaleMinQuantity,String imageUrl,PaymentPolicy policy,int advancePercent) {
        this.name=name;this.slug=slug;this.sku=sku;this.category=category;this.description=description;this.retailPrice=retailPrice;
        this.wholesalePrice=wholesalePrice;this.wholesaleMinQuantity=wholesaleMinQuantity;this.imageUrl=imageUrl;this.paymentPolicy=policy;this.advancePaymentPercent=advancePercent;
    }
    public void setActive(boolean active){this.active=active;}
}
