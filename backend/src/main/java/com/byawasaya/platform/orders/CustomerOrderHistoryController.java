package com.byawasaya.platform.orders;

import com.byawasaya.platform.auth.CustomerAuthController;
import com.byawasaya.platform.auth.CustomerAuthDtos;
import com.byawasaya.platform.auth.TokenService;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/orders")
public class CustomerOrderHistoryController {
    private final JdbcTemplate jdbc;

    public CustomerOrderHistoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public java.util.List<CustomerAuthDtos.OrderHistory> history(Authentication authentication) {
        TokenService.TokenPrincipal principal = CustomerAuthController.requireCustomer(authentication);
        java.util.UUID customerId = CustomerAuthController.accountId(principal);
        Boolean enabled = jdbc.queryForObject("SELECT enabled FROM customer_accounts WHERE id=?", Boolean.class, customerId);
        if (!Boolean.TRUE.equals(enabled))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Customer account is unavailable.");
        return jdbc.query("""
                SELECT id,business_id,store_name,store_slug,order_number,total_amount,payment_status,order_status,created_at
                  FROM customer_order_history
                 WHERE customer_account_id=?
                 ORDER BY created_at DESC
                 LIMIT 100
                """, (rs, row) -> {
                    Object created = rs.getObject("created_at");
                    Instant instant = created instanceof Instant value ? value
                            : created instanceof OffsetDateTime value ? value.toInstant()
                            : rs.getTimestamp("created_at").toInstant();
                    return new CustomerAuthDtos.OrderHistory(rs.getObject("id", java.util.UUID.class),
                            rs.getObject("business_id", java.util.UUID.class), rs.getString("store_name"),
                            rs.getString("store_slug"), rs.getString("order_number"), rs.getBigDecimal("total_amount"),
                            rs.getString("payment_status"), rs.getString("order_status"), instant);
                }, customerId);
    }
}
