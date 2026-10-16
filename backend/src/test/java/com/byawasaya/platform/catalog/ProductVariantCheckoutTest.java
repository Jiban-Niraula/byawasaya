package com.byawasaya.platform.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.byawasaya.platform.business.BusinessRepository;
import com.byawasaya.platform.business.TenantTableNameResolver;
import com.byawasaya.platform.plans.PlatformPlanRepository;

class ProductVariantCheckoutTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final TenantTableNameResolver tables = mock(TenantTableNameResolver.class);
    private final UUID businessId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private Product product;
    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, variants, mock(JdbcTemplate.class), tables,
                mock(BusinessRepository.class), mock(PlatformPlanRepository.class));
        product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(product.getBusinessId()).thenReturn(businessId);
        when(product.getName()).thenReturn("Tee");
        when(product.getSku()).thenReturn("TEE");
        when(product.getRetailPrice()).thenReturn(new BigDecimal("1000.00"));
        when(product.getWholesalePrice()).thenReturn(new BigDecimal("800.00"));
        when(product.getWholesaleMinQuantity()).thenReturn(5);
        when(product.getPaymentPolicy()).thenReturn(PaymentPolicy.COD_ALLOWED);
        when(product.getAdvancePaymentPercent()).thenReturn(30);
        when(product.isActive()).thenReturn(true);
        when(tables.prefix(businessId)).thenReturn(Optional.empty());
        when(products.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
    }

    @Test
    void requiresCustomerToChooseAProductVariant() {
        when(variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, productId))
                .thenReturn(List.of(variant("Red / Small", "950.00", "750.00", true)));
        assertThrows(ResponseStatusException.class, () -> service.findForCheckout(businessId, productId, null));
    }

    @Test
    void serverResolvesVariantPriceAndSku() {
        ProductVariant redSmall = variant("Red / Small", "950.00", "750.00", true);
        when(variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, productId))
                .thenReturn(List.of(redSmall));
        ProductService.ProductData result = service.findForCheckout(businessId, productId, redSmall.getId());
        assertEquals(new BigDecimal("950.00"), result.retailPrice());
        assertEquals(new BigDecimal("750.00"), result.wholesalePrice());
        assertEquals("TEE-RS", result.sku());
        assertEquals("Red / Small", result.variantLabel());
    }

    @Test
    void rejectsInactiveOrForeignVariantIds() {
        ProductVariant inactive = variant("Red / Small", "950.00", "750.00", false);
        when(variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, productId))
                .thenReturn(List.of(inactive));
        assertThrows(ResponseStatusException.class, () -> service.findForCheckout(businessId, productId, inactive.getId()));
        assertThrows(ResponseStatusException.class, () -> service.findForCheckout(businessId, productId, UUID.randomUUID()));
    }

    private ProductVariant variant(String label, String retail, String wholesale, boolean active) {
        String suffix = label.contains("Small") ? "RS" : "XX";
        return new ProductVariant(UUID.randomUUID(), businessId, productId, label, "TEE-" + suffix,
                new BigDecimal(retail), new BigDecimal(wholesale), active, 0);
    }
}
