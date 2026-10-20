package vn.com.tns_hrms.config.seed;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.entity.Permission;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.repository.RoleRepository;

@Component
public class RoleSeeder {
    public static final String USER_ROLE = "USER";
    public static final String CLIENT_ROLE = "CLIENT";

    // the only endpoints of the CLIENT role (all GET)
    private static final List<String> CLIENT_PERMISSION_PATHS = List.of(
            "/api/v1/attendance/users",
            "/api/v1/attendance/monthly",
            "/api/v1/attendance/daily");

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void seed(List<Permission> permissions) {
        log.info(">>> Init Roles");

        // Default USER permissions, as apiPath values.
        List<String> userPermissionPaths = List.of();

        initRole(USER_ROLE, "Standard user", permissions, userPermissionPaths);

        // ADMIN is granted every permission (and bypasses the permission check anyway).
        initRole(Role.ADMIN, "Administrator", permissions, null);

        // CLIENT: for the applications that only read attendance. Created once and then left alone, so the
        // permissions an admin changes later are never put back.
        createRoleIfMissing(CLIENT_ROLE, "Client that can only read attendance", permissions, "GET",
                CLIENT_PERMISSION_PATHS);
    }

    private void createRoleIfMissing(String roleName, String description, List<Permission> allPermissions,
            String method, List<String> permissionPaths) {
        if (roleRepository.existsByName(roleName)) {
            return;
        }

        Set<Permission> permissions = new HashSet<>();
        for (Permission permission : allPermissions) {
            if (method.equals(permission.getMethod()) && permissionPaths.contains(permission.getApiPath())) {
                permissions.add(permission);
            }
        }

        Role role = new Role();
        role.setName(roleName);
        role.setDescription(description);
        role.setPermissions(permissions);
        roleRepository.save(role);
        log.info("Created role {} with {} permissions", roleName, permissions.size());
    }

    private void initRole(String roleName, String description, List<Permission> allPermissions,
            List<String> permissionPaths) {
        Set<Permission> defaultPermissions = new HashSet<>();
        for (Permission permission : allPermissions) {
            if (permissionPaths == null || permissionPaths.contains(permission.getApiPath())) {
                defaultPermissions.add(permission);
            }
        }

        roleRepository.findWithPermissionsByName(roleName).ifPresentOrElse(role -> {
            // Existing roles only gain missing defaults; grants an admin has changed are never removed.
            int before = role.getPermissions().size();
            role.getPermissions().addAll(defaultPermissions);
            if (role.getPermissions().size() > before) {
                roleRepository.save(role);
                log.info("Updated role {} with new default permissions", roleName);
            }
        }, () -> {
            Role role = new Role();
            role.setName(roleName);
            role.setDescription(description);
            role.setPermissions(defaultPermissions);
            roleRepository.save(role);
            log.info("Created role {} with {} permissions", roleName, defaultPermissions.size());
        });
    }
}
