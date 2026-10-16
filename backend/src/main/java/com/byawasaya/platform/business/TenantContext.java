package com.byawasaya.platform.business;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import com.byawasaya.platform.auth.TokenService;
public final class TenantContext {
 private TenantContext(){}
 public static UUID requireBusinessId(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth!=null && auth.getDetails() instanceof TokenService.TokenPrincipal p && p.businessId()!=null)return p.businessId();throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A store-owner account is required.");}
}
