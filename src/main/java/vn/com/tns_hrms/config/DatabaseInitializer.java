package vn.com.tns_hrms.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.config.seed.AdminAccountSeeder;
import vn.com.tns_hrms.config.seed.PermissionSeeder;
import vn.com.tns_hrms.config.seed.RoleSeeder;
import vn.com.tns_hrms.entity.Permission;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final PermissionSeeder permissionSeeder;
    private final RoleSeeder roleSeeder;
    private final AdminAccountSeeder adminAccountSeeder;

    public DatabaseInitializer(PermissionSeeder permissionSeeder, RoleSeeder roleSeeder,
            AdminAccountSeeder adminAccountSeeder) {
        this.permissionSeeder = permissionSeeder;
        this.roleSeeder = roleSeeder;
        this.adminAccountSeeder = adminAccountSeeder;
    }

    @Override
    public void run(String... args) {
        log.info(">>> START INIT DATABASE");
        List<Permission> permissions = permissionSeeder.seed();
        roleSeeder.seed(permissions);
        adminAccountSeeder.seed();
        log.info(">>> END INIT DATABASE");
    }
}
