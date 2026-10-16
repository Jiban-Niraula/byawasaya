package com.byawasaya.platform.admin;
import java.math.BigDecimal; import java.time.Instant; import java.util.List; import java.util.UUID;
import com.byawasaya.platform.business.Business; import com.byawasaya.platform.business.BusinessStatus; import com.byawasaya.platform.auth.UserAccount;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size;
public final class AdminDtos {
 private AdminDtos(){}
 public record CreateBusinessRequest(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=120) String ownerName,@NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Z][A-Z0-9_]{2,39}") String plan){}
 public record BusinessResponse(UUID id,String name,String slug,String tenantTag,String ownerName,String ownerEmail,String phone,String plan,BusinessStatus status,Instant createdAt) implements java.io.Serializable {public static BusinessResponse from(Business b){return new BusinessResponse(b.getId(),b.getName(),b.getSlug(),b.getTenantTag(),b.getOwnerName(),b.getOwnerEmail(),b.getPhone(),b.getPlanCode(),b.getStatus(),b.getCreatedAt());}}
 public record DashboardResponse(long totalBusinesses,long activeBusinesses,long pendingReviews,long totalOrders,BigDecimal grossSales,List<BusinessResponse> recentBusinesses) implements java.io.Serializable {}
 public record StatusRequest(@NotNull BusinessStatus status){}
 public record UserResponse(UUID id, String username, String role, UUID businessId, String businessName, boolean enabled, Instant createdAt) {
  public static UserResponse from(UserAccount u, String businessName) { return new UserResponse(u.getId(), u.getUsername(), u.getRole(), u.getBusinessId(), businessName, u.isEnabled(), u.getCreatedAt()); }
 }
 public record EnabledRequest(@NotNull Boolean enabled) {}
}
