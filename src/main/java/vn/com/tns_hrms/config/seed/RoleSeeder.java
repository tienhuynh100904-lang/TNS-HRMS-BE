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
import vn.com.tns_hrms.service.AccountService;

@Component
public class RoleSeeder {
    public static final String USER_ROLE = "USER";

    private static final Logger log = LoggerFactory.getLogger(RoleSeeder.class);

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void seed(List<Permission> permissions) {
        log.info(">>> Init Roles");

        // Default USER permissions, as apiPath values. /api/v1/auth/** needs no permission.
        List<String> userPermissionPaths = List.of();

        initRole(USER_ROLE, "Standard user", permissions, userPermissionPaths);

        // ADMIN is granted every permission (and bypasses the permission check anyway).
        initRole(AccountService.ADMIN_ROLE, "Administrator", permissions, null);
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
