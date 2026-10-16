package com.byawasaya.platform.audit;
import java.util.UUID; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service public class AuditService {
 private final JdbcTemplate jdbc; public AuditService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(String actor,String role,UUID tenant,String action,String targetType,String targetId,String outcome,String details){
  record(actor,role,tenant,action,targetType,targetId,outcome,details,null);
 }
 public void record(String actor,String role,UUID tenant,String action,String targetType,String targetId,String outcome,String details,jakarta.servlet.http.HttpServletRequest request){
  String safeDetails=details==null?"{}":details;
  if(request==null){
   jdbc.update("INSERT INTO audit_logs(id,actor_username,actor_role,tenant_id,action,target_type,target_id,outcome,details) VALUES (gen_random_uuid(),?,?,?,?,?,?,?,CAST(? AS jsonb))",actor,role,tenant,action,targetType,targetId,outcome,safeDetails);
   return;
  }
  String userAgent=request.getHeader("User-Agent");
  if(userAgent!=null&&userAgent.length()>500)userAgent=userAgent.substring(0,500);
  jdbc.update("INSERT INTO audit_logs(id,actor_username,actor_role,tenant_id,action,target_type,target_id,outcome,ip_address,user_agent,details) VALUES (gen_random_uuid(),?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))",actor,role,tenant,action,targetType,targetId,outcome,request.getRemoteAddr(),userAgent,safeDetails);
 }
}
