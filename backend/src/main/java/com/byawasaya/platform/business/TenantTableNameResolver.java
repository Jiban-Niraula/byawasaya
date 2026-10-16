package com.byawasaya.platform.business;
import java.util.Optional; import java.util.UUID; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Component;
/** Resolves physical tenant table identifiers only from the trusted provisioning registry. */
@Component public class TenantTableNameResolver {
 private final JdbcTemplate jdbc; public TenantTableNameResolver(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<String> prefix(UUID businessId){return jdbc.query("SELECT table_prefix FROM tenant_provisioning WHERE business_id=? AND status='READY'",rs->rs.next()?Optional.of(validate(rs.getString(1))):Optional.empty(),businessId);}
 public String validate(String prefix){if(prefix==null||!prefix.matches("t_[a-z0-9]{8}"))throw new IllegalStateException("Invalid tenant table prefix stored in registry.");return prefix;}
 public String table(String prefix,String suffix){if(!java.util.Set.of("products","product_variants","categories","staffs","customers","store_assets","orders","order_items","payments","payment_events","store_settings").contains(suffix))throw new IllegalArgumentException("Unsupported tenant table.");return validate(prefix)+"_"+suffix;}
}
