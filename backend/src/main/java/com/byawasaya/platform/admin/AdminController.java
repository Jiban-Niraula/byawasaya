package com.byawasaya.platform.admin;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
 private final AdminService service;
 public AdminController(AdminService service){this.service=service;}
 @GetMapping("/dashboard") public AdminDtos.DashboardResponse dashboard(){return service.getDashboard();}
 @GetMapping("/businesses/recent") public List<AdminDtos.BusinessResponse> recent(){return service.getRecentBusinesses();}
 @GetMapping("/businesses") public List<AdminDtos.BusinessResponse> businesses(){return service.getBusinesses();}
 @PostMapping("/businesses") @PreAuthorize("hasRole('SUPERADMIN')") public AdminDtos.BusinessResponse create(@Valid @RequestBody AdminDtos.CreateBusinessRequest r){throw new ResponseStatusException(HttpStatus.GONE,"New stores must be created through the tenant application verification workflow.");}
 @PatchMapping("/businesses/{id}/status") @PreAuthorize("hasRole('SUPERADMIN')") public AdminDtos.BusinessResponse status(@PathVariable UUID id,@Valid @RequestBody AdminDtos.StatusRequest r,Authentication auth){return service.updateStatus(id,r.status(),auth.getName());}
 @GetMapping("/users") public List<AdminDtos.UserResponse> users(){return service.getUsers();}
 @PatchMapping("/users/{id}/enabled") @PreAuthorize("hasRole('SUPERADMIN')") public AdminDtos.UserResponse enabled(@PathVariable UUID id,@Valid @RequestBody AdminDtos.EnabledRequest r,Authentication auth){var target=service.getUsers().stream().filter(u->u.id().equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));if(!r.enabled()&&target.username().equalsIgnoreCase(auth.getName()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot disable your own account.");return service.setUserEnabled(id,r.enabled(),auth.getName());}
}
