package com.byawasaya.platform.onboarding;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Applies additive tenant table changes to already provisioned stores before serving requests. */
@Component
@Order(0)
public class TenantSchemaUpgradeRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantProvisioningService provisioning;

    public TenantSchemaUpgradeRunner(JdbcTemplate jdbc, TenantProvisioningService provisioning) {
        this.jdbc = jdbc;
        this.provisioning = provisioning;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer registryTable = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE lower(table_name)='tenant_provisioning'",
                Integer.class);
        if (registryTable == null || registryTable == 0) return;
        var tenants = jdbc.query("SELECT business_id,table_prefix FROM tenant_provisioning WHERE status='READY' AND schema_version<3",
                (rs, row) -> new Tenant(UUID.fromString(rs.getString("business_id")), rs.getString("table_prefix")));
        for (Tenant tenant : tenants) provisioning.provision(tenant.businessId(), tenant.prefix());
    }

    private record Tenant(UUID businessId, String prefix) {}
}
