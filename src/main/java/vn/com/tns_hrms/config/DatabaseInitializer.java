package vn.com.tns_hrms.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.config.seed.AdminClientSeeder;
import vn.com.tns_hrms.config.seed.BranchSeeder;
import vn.com.tns_hrms.config.seed.DepartmentSeeder;
import vn.com.tns_hrms.config.seed.EmployeeSeeder;
import vn.com.tns_hrms.config.seed.PermissionSeeder;
import vn.com.tns_hrms.config.seed.RoleSeeder;
import vn.com.tns_hrms.entity.Permission;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final PermissionSeeder permissionSeeder;
    private final RoleSeeder roleSeeder;
    private final AdminClientSeeder adminClientSeeder;
    private final BranchSeeder branchSeeder;
    private final DepartmentSeeder departmentSeeder;
    private final EmployeeSeeder employeeSeeder;

    public DatabaseInitializer(PermissionSeeder permissionSeeder, RoleSeeder roleSeeder,
            AdminClientSeeder adminClientSeeder, BranchSeeder branchSeeder, DepartmentSeeder departmentSeeder,
            EmployeeSeeder employeeSeeder) {
        this.permissionSeeder = permissionSeeder;
        this.roleSeeder = roleSeeder;
        this.adminClientSeeder = adminClientSeeder;
        this.branchSeeder = branchSeeder;
        this.departmentSeeder = departmentSeeder;
        this.employeeSeeder = employeeSeeder;
    }

    @Override
    public void run(String... args) {
        log.info(">>> START INIT DATABASE");
        List<Permission> permissions = permissionSeeder.seed();
        roleSeeder.seed(permissions);
        adminClientSeeder.seed();
        branchSeeder.seed();
        departmentSeeder.seed();
        employeeSeeder.seed();
        log.info(">>> END INIT DATABASE");
    }
}
