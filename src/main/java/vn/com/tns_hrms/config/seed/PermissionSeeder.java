package vn.com.tns_hrms.config.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.entity.Permission;
import vn.com.tns_hrms.repository.PermissionRepository;

/**
 * Registers one permission per protected endpoint. Add the permissions of every new module here;
 * existing rows are matched by (apiPath, method) so ids - and the role grants pointing at them - are kept.
 */
@Component
public class PermissionSeeder {
    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private final PermissionRepository permissionRepository;

    public PermissionSeeder(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public List<Permission> seed() {
        log.info(">>> Init Permissions");

        List<Permission> permissions = List.of(
                // Client (OAuth2 clients that call the API)
                new Permission("Get All Clients", "/api/v1/clients", "GET", "CLIENT"),
                new Permission("Get Client Detail", "/api/v1/clients/{id}", "GET", "CLIENT"),
                new Permission("Create Client", "/api/v1/clients", "POST", "CLIENT"),
                new Permission("Update Client", "/api/v1/clients", "PUT", "CLIENT"),
                new Permission("Renew Client Secret", "/api/v1/clients/{id}/secret", "POST", "CLIENT"),

                // Role
                new Permission("Get All Roles", "/api/v1/roles", "GET", "ROLE"),
                new Permission("Get Role Detail", "/api/v1/roles/{id}", "GET", "ROLE"),
                new Permission("Create Role", "/api/v1/roles", "POST", "ROLE"),
                new Permission("Update Role", "/api/v1/roles", "PUT", "ROLE"),
                new Permission("Delete Role", "/api/v1/roles/{id}", "DELETE", "ROLE"),
                new Permission("Get Role Stats", "/api/v1/roles/stats", "GET", "ROLE"),

                // Permission
                new Permission("Get All Permissions (paged)", "/api/v1/permissions", "GET", "PERMISSION"),
                new Permission("Get Permission Detail", "/api/v1/permissions/{id}", "GET", "PERMISSION"),
                new Permission("Create Permission", "/api/v1/permissions", "POST", "PERMISSION"),
                new Permission("Update Permission", "/api/v1/permissions", "PUT", "PERMISSION"),
                new Permission("Delete Permission", "/api/v1/permissions/{id}", "DELETE", "PERMISSION"),
                new Permission("Get Permission Stats", "/api/v1/permissions/stats", "GET", "PERMISSION"),
                new Permission("Get Permission Modules", "/api/v1/permissions/modules", "GET", "PERMISSION"),
                new Permission("Get All Permissions", "/api/v1/permissions/all", "GET", "PERMISSION"),

                // Attendance
                new Permission("Get Punch Users", "/api/v1/attendance/users", "GET", "ATTENDANCE"),
                new Permission("Get Daily Attendance", "/api/v1/attendance/daily", "GET", "ATTENDANCE"),
                new Permission("Pull Punches Now", "/api/v1/attendance/pull", "POST", "ATTENDANCE"),
                new Permission("Get Employee Punches", "/api/v1/attendance/monthly", "GET", "ATTENDANCE"),

                // Employee
                new Permission("Get All Employees", "/api/v1/employees", "GET", "EMPLOYEE"),
                new Permission("Update Employee", "/api/v1/employees", "PUT", "EMPLOYEE"),
                new Permission("Delete Employee", "/api/v1/employees/{id}", "DELETE", "EMPLOYEE"));

        removeAccountPermissions();

        for (Permission permission : permissions) {
            permissionRepository.findByApiPathAndMethod(permission.getApiPath(), permission.getMethod())
                    .ifPresentOrElse(existing -> {
                        existing.setName(permission.getName());
                        existing.setModule(permission.getModule());
                        permissionRepository.save(existing);
                    }, () -> permissionRepository.save(permission));
        }

        return permissionRepository.findAll();
    }

    // The account endpoints no longer exist (callers are OAuth2 clients now): drop their permissions from an old
    // database, and from the roles they were granted to.
    private void removeAccountPermissions() {
        for (Permission stale : permissionRepository.findAll()) {
            if ("ACCOUNT".equals(stale.getModule()) || stale.getApiPath().startsWith("/api/v1/accounts")) {
                stale.getRoles().forEach(role -> role.getPermissions().remove(stale));
                permissionRepository.delete(stale);
                log.info("Removed obsolete permission {} {}", stale.getMethod(), stale.getApiPath());
            }
        }
    }
}
