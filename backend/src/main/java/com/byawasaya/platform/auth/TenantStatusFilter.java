package com.byawasaya.platform.auth;
import java.io.IOException; import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.http.MediaType; import org.springframework.security.core.Authentication; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.web.filter.OncePerRequestFilter; import com.fasterxml.jackson.databind.ObjectMapper; import com.byawasaya.platform.business.*; import com.byawasaya.platform.api.ApiError;
/** Blocks operational tenant APIs until the tenant is approved and its trial/subscription is active. */
public class TenantStatusFilter extends OncePerRequestFilter {
 private final BusinessRepository businesses; private final ObjectMapper mapper;
 public TenantStatusFilter(BusinessRepository businesses,ObjectMapper mapper){this.businesses=businesses;this.mapper=mapper;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String path=req.getRequestURI();if(!(path.startsWith("/api/merchant/")||path.startsWith("/api/tenant/")||path.startsWith("/api/platform/tenant-workspace/"))){chain.doFilter(req,res);return;}
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();if(auth!=null&&auth.getDetails() instanceof TokenService.TokenPrincipal p&&p.businessId()!=null){Business b=businesses.findById(p.businessId()).orElse(null);if(b==null||!b.isOperational()){res.setStatus(403);res.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(res.getOutputStream(),new ApiError(java.time.Instant.now(),403,"Store not active","Your store must be approved and active before using this workspace.",java.util.Map.of("status",b==null?"MISSING":(b.getStatus()==BusinessStatus.ACTIVE&&b.getTrialEndsAt()!=null&&b.getTrialEndsAt().isBefore(java.time.Instant.now())?"TRIAL_EXPIRED":b.getStatus().name()))));return;}}
  chain.doFilter(req,res);
 }
}
