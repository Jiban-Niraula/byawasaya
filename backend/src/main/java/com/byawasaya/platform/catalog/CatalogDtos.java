package com.byawasaya.platform.catalog;

import java.math.BigDecimal; import java.time.Instant; import java.util.List; import java.util.UUID;
import jakarta.validation.Valid; import jakarta.validation.constraints.DecimalMin; import jakarta.validation.constraints.Max; import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Pattern; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size;

public final class CatalogDtos {
 private CatalogDtos(){}
 public record VariantRequest(UUID id,@NotBlank @Size(max=140) String label,@Size(max=80) String sku,@DecimalMin("0.01") BigDecimal retailPrice,@DecimalMin("0.01") BigDecimal wholesalePrice,boolean active){}
 public record VariantResponse(UUID id,String label,String sku,BigDecimal retailPrice,BigDecimal wholesalePrice,boolean active) { static VariantResponse from(ProductVariant v){return new VariantResponse(v.getId(),v.getLabel(),v.getSku(),v.getRetailPrice(),v.getWholesalePrice(),v.isActive());} }
 public record ProductRequest(@NotBlank @Size(max=140) String name,@Size(max=80) String sku,@NotBlank @Size(max=80) String category,@Size(max=3000) String description,@NotNull @DecimalMin("0.01") BigDecimal retailPrice,@NotNull @DecimalMin("0.01") BigDecimal wholesalePrice,@Min(2) @Max(10000) int wholesaleMinQuantity,@Size(max=1000) @Pattern(regexp="^$|https?://.+", message="Use an http(s) image URL.") String imageUrl,@NotNull PaymentPolicy paymentPolicy,@Min(0) @Max(100) int advancePaymentPercent,@Size(max=40) List<@Valid VariantRequest> variants){}
 public record ProductResponse(UUID id,UUID businessId,String name,String slug,String sku,String category,String description,BigDecimal retailPrice,BigDecimal wholesalePrice,int wholesaleMinQuantity,String imageUrl,PaymentPolicy paymentPolicy,int advancePaymentPercent,boolean active,Instant createdAt,List<VariantResponse> variants){public static ProductResponse from(Product p){return new ProductResponse(p.getId(),p.getBusinessId(),p.getName(),p.getSlug(),p.getSku(),p.getCategory(),p.getDescription(),p.getRetailPrice(),p.getWholesalePrice(),p.getWholesaleMinQuantity(),p.getImageUrl(),p.getPaymentPolicy(),p.getAdvancePaymentPercent(),p.isActive(),p.getCreatedAt(),List.of());}}
}
