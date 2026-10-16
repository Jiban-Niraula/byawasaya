package com.byawasaya.platform.catalog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.byawasaya.platform.business.BusinessRepository;
import com.byawasaya.platform.business.TenantTableNameResolver;
import com.byawasaya.platform.plans.PlatformPlanRepository;

@Service
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final JdbcTemplate jdbc;
    private final TenantTableNameResolver tables;
    private final BusinessRepository businesses;
    private final PlatformPlanRepository plans;

    public ProductService(ProductRepository products, ProductVariantRepository variants, JdbcTemplate jdbc,
            TenantTableNameResolver tables, BusinessRepository businesses, PlatformPlanRepository plans) {
        this.products = products;
        this.variants = variants;
        this.jdbc = jdbc;
        this.tables = tables;
        this.businesses = businesses;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductResponse> list(UUID businessId, boolean activeOnly) {
        Optional<String> prefix = tables.prefix(businessId);
        if (prefix.isPresent()) {
            String table = tables.table(prefix.get(), "products");
            return jdbc.query("SELECT id,name,slug,sku,category,description,retail_price,wholesale_price,wholesale_min_quantity,image_url,payment_policy,advance_payment_percent,active,created_at FROM "
                    + table + (activeOnly ? " WHERE active=true AND payment_policy<>'ONLINE_ONLY'" : "") + " ORDER BY created_at DESC",
                    (rs, n) -> fromRow(rs, businessId, tenantVariants(prefix.get(), rs.getObject("id", UUID.class))));
        }
        List<Product> rows = activeOnly ? products.findByBusinessIdAndActiveTrueOrderByCreatedAtDesc(businessId)
                : products.findByBusinessIdOrderByCreatedAtDesc(businessId);
        return rows.stream().filter(p -> !activeOnly || p.getPaymentPolicy() != PaymentPolicy.ONLINE_ONLY)
                .map(p -> response(p, variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, p.getId())))
                .toList();
    }

    public CatalogDtos.ProductResponse create(UUID businessId, CatalogDtos.ProductRequest r) {
        enforceProductLimit(businessId);
        validatePaymentPolicy(businessId, r.paymentPolicy());
        List<CatalogDtos.VariantRequest> requestedVariants = normalizeVariants(r.variants(), businessId, null);
        Optional<String> prefix = tables.prefix(businessId);
        if (prefix.isPresent()) validateImageUrl(businessId, prefix.get(), r.imageUrl());
        String slug = slugify(r.name()) + "-" + UUID.randomUUID().toString().substring(0, 5);
        String sku = blankToNull(r.sku());
        if (prefix.isPresent()) {
            String table = tables.table(prefix.get(), "products");
            UUID id = UUID.randomUUID();
            try {
                jdbc.update("INSERT INTO " + table + "(id,name,slug,sku,category,description,retail_price,wholesale_price,wholesale_min_quantity,image_url,payment_policy,advance_payment_percent,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,true)",
                        id, r.name().trim(), slug, sku, r.category().trim(), r.description(), r.retailPrice(), r.wholesalePrice(),
                        r.wholesaleMinQuantity(), blankToNull(r.imageUrl()), r.paymentPolicy().name(), r.advancePaymentPercent());
                replaceTenantVariants(prefix.get(), id, requestedVariants);
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product or variant SKU is already in use.");
            }
            return findResponse(businessId, id);
        }
        try {
            Product product = new Product(businessId, r.name().trim(), slug, sku, r.category().trim(), r.description(),
                    r.retailPrice(), r.wholesalePrice(), r.wholesaleMinQuantity(), blankToNull(r.imageUrl()),
                    r.paymentPolicy(), r.advancePaymentPercent());
            Product saved = products.save(product);
            replaceLegacyVariants(businessId, saved.getId(), requestedVariants);
            return response(saved, variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, saved.getId()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product or variant SKU is already in use.");
        }
    }

    public CatalogDtos.ProductResponse update(UUID businessId, UUID id, CatalogDtos.ProductRequest r) {
        validatePaymentPolicy(businessId, r.paymentPolicy());
        Optional<String> prefix = tables.prefix(businessId);
        if (prefix.isPresent()) {
            String table = tables.table(prefix.get(), "products");
            String oldImage;
            try { oldImage = jdbc.queryForObject("SELECT image_url FROM " + table + " WHERE id=?", String.class, id); }
            catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."); }
            if (!java.util.Objects.equals(oldImage, blankToNull(r.imageUrl()))) validateImageUrl(businessId, prefix.get(), r.imageUrl());
            List<CatalogDtos.VariantRequest> requestedVariants = normalizeVariants(r.variants(), businessId, id);
            try {
                int count = jdbc.update("UPDATE " + table + " SET name=?,sku=?,category=?,description=?,retail_price=?,wholesale_price=?,wholesale_min_quantity=?,image_url=?,payment_policy=?,advance_payment_percent=? WHERE id=?",
                        r.name().trim(), blankToNull(r.sku()), r.category().trim(), r.description(), r.retailPrice(), r.wholesalePrice(),
                        r.wholesaleMinQuantity(), blankToNull(r.imageUrl()), r.paymentPolicy().name(), r.advancePaymentPercent(), id);
                if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
                replaceTenantVariants(prefix.get(), id, requestedVariants);
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product or variant SKU is already in use.");
            }
            return findResponse(businessId, id);
        }
        Product product = products.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        List<CatalogDtos.VariantRequest> requestedVariants = normalizeVariants(r.variants(), businessId, id);
        try {
            product.update(r.name().trim(), product.getSlug(), blankToNull(r.sku()), r.category().trim(), r.description(),
                    r.retailPrice(), r.wholesalePrice(), r.wholesaleMinQuantity(), blankToNull(r.imageUrl()),
                    r.paymentPolicy(), r.advancePaymentPercent());
            replaceLegacyVariants(businessId, id, requestedVariants);
            return response(product, variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, id));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product or variant SKU is already in use.");
        }
    }

    public void hide(UUID businessId, UUID id) {
        Optional<String> prefix = tables.prefix(businessId);
        if (prefix.isPresent()) {
            int count = jdbc.update("UPDATE " + tables.table(prefix.get(), "products") + " SET active=false WHERE id=?", id);
            if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
            return;
        }
        Product product = products.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        product.setActive(false);
    }

    @Transactional(readOnly = true)
    public ProductData findForCheckout(UUID businessId, UUID id, UUID variantId) {
        Optional<String> prefix = tables.prefix(businessId);
        ProductData product;
        List<VariantData> activeVariants;
        if (prefix.isPresent()) {
            List<ProductData> rows = jdbc.query("SELECT id,name,sku,retail_price,wholesale_price,wholesale_min_quantity,payment_policy,advance_payment_percent,active FROM "
                    + tables.table(prefix.get(), "products") + " WHERE id=? AND active=true",
                    (rs, n) -> new ProductData(rs.getObject("id", UUID.class), businessId, rs.getString("name"), rs.getString("sku"),
                            rs.getBigDecimal("retail_price"), rs.getBigDecimal("wholesale_price"), rs.getInt("wholesale_min_quantity"),
                            PaymentPolicy.valueOf(rs.getString("payment_policy")), rs.getInt("advance_payment_percent"), rs.getBoolean("active"), null, null), id);
            product = rows.stream().findFirst().orElseThrow(() -> unavailableProduct());
            activeVariants = tenantVariants(prefix.get(), id).stream().filter(ProductVariant::isActive).map(this::variantData).toList();
        } else {
            Product p = products.findByIdAndBusinessId(id, businessId).filter(Product::isActive).orElseThrow(() -> unavailableProduct());
            product = new ProductData(p.getId(), businessId, p.getName(), p.getSku(), p.getRetailPrice(), p.getWholesalePrice(),
                    p.getWholesaleMinQuantity(), p.getPaymentPolicy(), p.getAdvancePaymentPercent(), p.isActive(), null, null);
            activeVariants = variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, id)
                    .stream().filter(ProductVariant::isActive).map(this::variantData).toList();
        }
        if (variantId == null) {
            if (!activeVariants.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a variant for " + product.name() + ".");
            return product;
        }
        VariantData selected = activeVariants.stream().filter(v -> v.id().equals(variantId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the selected product variants is unavailable."));
        return new ProductData(product.id(), businessId, product.name(), selected.sku() == null ? product.sku() : selected.sku(),
                selected.retailPrice() == null ? product.retailPrice() : selected.retailPrice(),
                selected.wholesalePrice() == null ? product.wholesalePrice() : selected.wholesalePrice(),
                product.wholesaleMinQuantity(), product.paymentPolicy(), product.advancePaymentPercent(), product.active(),
                selected.id(), selected.label());
    }

    public boolean usesTenantTables(UUID businessId) { return tables.prefix(businessId).isPresent(); }
    public String prefix(UUID businessId) { return tables.prefix(businessId).orElseThrow(); }

    private CatalogDtos.ProductResponse findResponse(UUID businessId, UUID id) {
        String table = tables.table(prefix(businessId), "products");
        return jdbc.query("SELECT id,name,slug,sku,category,description,retail_price,wholesale_price,wholesale_min_quantity,image_url,payment_policy,advance_payment_percent,active,created_at FROM " + table + " WHERE id=?",
                rs -> { if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
                    return fromRow(rs, businessId, tenantVariants(prefix(businessId), id)); }, id);
    }

    private CatalogDtos.ProductResponse response(Product p, List<ProductVariant> values) {
        return new CatalogDtos.ProductResponse(p.getId(), p.getBusinessId(), p.getName(), p.getSlug(), p.getSku(), p.getCategory(),
                p.getDescription(), p.getRetailPrice(), p.getWholesalePrice(), p.getWholesaleMinQuantity(), p.getImageUrl(),
                p.getPaymentPolicy(), p.getAdvancePaymentPercent(), p.isActive(), p.getCreatedAt(),
                values.stream().map(CatalogDtos.VariantResponse::from).toList());
    }

    private CatalogDtos.ProductResponse fromRow(ResultSet rs, UUID businessId, List<ProductVariant> values) throws SQLException {
        return new CatalogDtos.ProductResponse(rs.getObject("id", UUID.class), businessId, rs.getString("name"), rs.getString("slug"),
                rs.getString("sku"), rs.getString("category"), rs.getString("description"), rs.getBigDecimal("retail_price"),
                rs.getBigDecimal("wholesale_price"), rs.getInt("wholesale_min_quantity"), rs.getString("image_url"),
                PaymentPolicy.valueOf(rs.getString("payment_policy")), rs.getInt("advance_payment_percent"), rs.getBoolean("active"),
                toInstant(rs.getObject("created_at")), values.stream().map(CatalogDtos.VariantResponse::from).toList());
    }

    private List<ProductVariant> tenantVariants(String prefix, UUID productId) {
        return jdbc.query("SELECT id,product_id,label,sku,retail_price,wholesale_price,active,sort_order,created_at FROM "
                        + tables.table(prefix, "product_variants") + " WHERE product_id=? ORDER BY sort_order,created_at",
                (rs, n) -> new ProductVariant(rs.getObject("id", UUID.class), null, rs.getObject("product_id", UUID.class),
                        rs.getString("label"), rs.getString("sku"), rs.getBigDecimal("retail_price"), rs.getBigDecimal("wholesale_price"),
                        rs.getBoolean("active"), rs.getInt("sort_order")), productId);
    }

    private VariantData variantData(ProductVariant v) {
        return new VariantData(v.getId(), v.getLabel(), v.getSku(), v.getRetailPrice(), v.getWholesalePrice(), v.isActive());
    }

    private List<CatalogDtos.VariantRequest> normalizeVariants(List<CatalogDtos.VariantRequest> input, UUID businessId, UUID productId) {
        List<CatalogDtos.VariantRequest> values = input == null ? List.of() : input;
        Set<String> labels = new HashSet<>();
        Set<String> skus = new HashSet<>();
        Set<UUID> existingIds = new HashSet<>();
        if (productId != null) {
            if (tables.prefix(businessId).isPresent()) tenantVariants(prefix(businessId), productId).forEach(v -> existingIds.add(v.getId()));
            else variants.findByBusinessIdAndProductIdOrderBySortOrderAscCreatedAtAsc(businessId, productId).forEach(v -> existingIds.add(v.getId()));
        }
        List<CatalogDtos.VariantRequest> normalized = new ArrayList<>();
        for (CatalogDtos.VariantRequest value : values) {
            String label = value.label().trim();
            if (!labels.add(label.toLowerCase(Locale.ROOT))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant names must be unique within a product.");
            String sku = blankToNull(value.sku());
            if (sku != null && !skus.add(sku.toLowerCase(Locale.ROOT))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant SKUs must be unique within a product.");
            if (value.id() != null && !existingIds.contains(value.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A variant ID does not belong to this product.");
            normalized.add(new CatalogDtos.VariantRequest(value.id() == null ? UUID.randomUUID() : value.id(), label, sku,
                    value.retailPrice(), value.wholesalePrice(), value.active()));
        }
        return normalized;
    }

    private void replaceTenantVariants(String prefix, UUID productId, List<CatalogDtos.VariantRequest> values) {
        String table = tables.table(prefix, "product_variants");
        jdbc.update("DELETE FROM " + table + " WHERE product_id=?", productId);
        for (int i = 0; i < values.size(); i++) {
            CatalogDtos.VariantRequest v = values.get(i);
            jdbc.update("INSERT INTO " + table + "(id,product_id,label,sku,retail_price,wholesale_price,active,sort_order) VALUES(?,?,?,?,?,?,?,?)",
                    v.id(), productId, v.label(), blankToNull(v.sku()), v.retailPrice(), v.wholesalePrice(), v.active(), i);
        }
    }

    private void replaceLegacyVariants(UUID businessId, UUID productId, List<CatalogDtos.VariantRequest> values) {
        variants.deleteByBusinessIdAndProductId(businessId, productId);
        List<ProductVariant> rows = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            CatalogDtos.VariantRequest v = values.get(i);
            rows.add(new ProductVariant(v.id(), businessId, productId, v.label(), blankToNull(v.sku()), v.retailPrice(), v.wholesalePrice(), v.active(), i));
        }
        variants.saveAll(rows);
    }

    private ResponseStatusException unavailableProduct() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the products is unavailable.");
    }

    private Instant toInstant(Object v) {
        if (v instanceof Instant i) return i;
        if (v instanceof OffsetDateTime o) return o.toInstant();
        if (v instanceof java.sql.Timestamp t) return t.toInstant();
        return Instant.now();
    }

    private void validateImageUrl(UUID businessId, String prefix, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        var business = businesses.findById(businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
        try {
            java.net.URI uri = java.net.URI.create(imageUrl.trim());
            String path = uri.isAbsolute() ? uri.getPath() : imageUrl.trim();
            String expectedPrefix = "/api/store/" + business.getSlug() + "/assets/";
            if (!path.startsWith(expectedPrefix)) throw new IllegalArgumentException();
            UUID assetId = UUID.fromString(path.substring(expectedPrefix.length()));
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tables.table(prefix, "store_assets") + " WHERE id=? AND asset_type='PRODUCT_IMAGE'", Integer.class, assetId);
            if (count == null || count != 1) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product images must be uploaded to Byawasaya storage before they can be attached.");
        }
    }

    private void validatePaymentPolicy(UUID businessId, PaymentPolicy policy) {
        if (policy == PaymentPolicy.ONLINE_ONLY) {
            var business = businesses.findById(businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
            if (!business.isOnlinePaymentEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Online-only products cannot be published until a payment gateway is integrated.");
        }
    }

    private void enforceProductLimit(UUID businessId) {
        var business = businesses.findById(businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found."));
        var plan = plans.findByCodeIgnoreCase(business.getPlanCode()).orElse(null);
        if (plan == null) return;
        long count = tables.prefix(businessId).map(p -> jdbc.queryForObject("SELECT COUNT(*) FROM " + tables.table(p, "products"), Long.class))
                .orElseGet(() -> products.countByBusinessId(businessId));
        if (count >= plan.getMaxProducts()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Your current plan allows at most " + plan.getMaxProducts() + " products. Upgrade the plan to add more.");
    }

    public static String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isBlank() ? "product" : slug;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record VariantData(UUID id, String label, String sku, BigDecimal retailPrice, BigDecimal wholesalePrice, boolean active) {}
    public record ProductData(UUID id, UUID businessId, String name, String sku, BigDecimal retailPrice,
            BigDecimal wholesalePrice, int wholesaleMinQuantity, PaymentPolicy paymentPolicy,
            int advancePaymentPercent, boolean active, UUID variantId, String variantLabel) {}
}
