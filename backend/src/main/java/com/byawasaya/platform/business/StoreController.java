package com.byawasaya.platform.business;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping; import org.springframework.web.bind.annotation.PathVariable; import org.springframework.web.bind.annotation.PatchMapping; import org.springframework.web.bind.annotation.RequestBody; import org.springframework.web.bind.annotation.RequestMapping; import org.springframework.web.bind.annotation.RestController;
import com.byawasaya.platform.catalog.CatalogDtos; import com.byawasaya.platform.catalog.ProductService; import com.byawasaya.platform.orders.OrderDtos; import com.byawasaya.platform.orders.OrderService; import com.byawasaya.platform.payments.PaymentGatewayService; import com.byawasaya.platform.payments.PaymentProviderDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
@RestController
public class StoreController {
 private final StoreService stores; private final ProductService products; private final OrderService orders; private final PaymentGatewayService payments; private final TenantStoreHostResolver hostResolver;
 public StoreController(StoreService stores,ProductService products,OrderService orders,PaymentGatewayService payments,TenantStoreHostResolver hostResolver){this.stores=stores;this.products=products;this.orders=orders;this.payments=payments;this.hostResolver=hostResolver;}
 @GetMapping("/api/store/{slug}") public StoreDtos.PublicStoreResponse publicStore(@PathVariable String slug){return stores.publicStore(slug);}
 @GetMapping("/api/storefront/current") public StoreDtos.PublicStoreResponse publicStoreForHost(jakarta.servlet.http.HttpServletRequest request){String slug=hostResolver.tenantSlug(request.getServerName()).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"No store is configured for this hostname."));return stores.publicStore(slug);}
 @org.springframework.web.bind.annotation.PostMapping("/api/store/{slug}/orders") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public PaymentProviderDtos.CheckoutResponse checkout(@PathVariable String slug,@Valid @RequestBody OrderDtos.CreateOrderRequest request,@org.springframework.web.bind.annotation.RequestHeader(value="Idempotency-Key") String idempotencyKey,Authentication authentication){java.util.UUID customerAccountId=null;if(authentication!=null&&authentication.getDetails() instanceof com.byawasaya.platform.auth.TokenService.TokenPrincipal principal&&"CUSTOMER".equals(principal.role()))customerAccountId=com.byawasaya.platform.auth.CustomerAuthController.accountId(principal);var order=orders.checkout(slug,request,idempotencyKey,customerAccountId);return payments.begin(slug,order,request.paymentMethod(),request.paymentProvider());}
 @GetMapping("/api/merchant/store") public StoreDtos.StoreResponse profile(){return stores.profile(TenantContext.requireBusinessId());}
 @PatchMapping("/api/merchant/store") public StoreDtos.StoreResponse update(@Valid @RequestBody StoreDtos.UpdateSettingsRequest r){return stores.update(TenantContext.requireBusinessId(),r);}
 @GetMapping("/api/merchant/products") public java.util.List<CatalogDtos.ProductResponse> products(){return products.list(TenantContext.requireBusinessId(),false);}
 @org.springframework.web.bind.annotation.PostMapping("/api/merchant/products") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public CatalogDtos.ProductResponse createProduct(@Valid @RequestBody CatalogDtos.ProductRequest r){return products.create(TenantContext.requireBusinessId(),r);}
 @PatchMapping("/api/merchant/products/{id}") public CatalogDtos.ProductResponse updateProduct(@PathVariable UUID id,@Valid @RequestBody CatalogDtos.ProductRequest r){return products.update(TenantContext.requireBusinessId(),id,r);}
 @org.springframework.web.bind.annotation.DeleteMapping("/api/merchant/products/{id}") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void hideProduct(@PathVariable UUID id){products.hide(TenantContext.requireBusinessId(),id);}
 @GetMapping("/api/merchant/orders") public java.util.List<OrderDtos.OrderResponse> orders(){return orders.listForStore(TenantContext.requireBusinessId());}
 @PatchMapping("/api/merchant/orders/{id}/status") public OrderDtos.OrderResponse updateOrder(@PathVariable UUID id,@Valid @RequestBody OrderDtos.UpdateOrderStatusRequest r){return orders.updateStatus(TenantContext.requireBusinessId(),id,r.status());}
 @PatchMapping("/api/merchant/orders/{id}/payment-status") public OrderDtos.OrderResponse updatePayment(@PathVariable UUID id,@Valid @RequestBody OrderDtos.UpdatePaymentStatusRequest r){return orders.updatePaymentStatus(TenantContext.requireBusinessId(),id,r.status());}
}
