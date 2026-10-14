package vn.com.tns_hrms.config.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.entity.Account;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.repository.AccountRepository;
import vn.com.tns_hrms.repository.RoleRepository;
import vn.com.tns_hrms.service.AccountService;

@Component
public class AdminAccountSeeder {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    public AdminAccountSeeder(AccountRepository accountRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void seed() {
        if (this.accountRepository.count() > 0) {
            return;
        }

        Role adminRole = this.roleRepository.findByName(AccountService.ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("ADMIN role must be seeded before the admin account"));

        Account admin = new Account();
        admin.setEmail(adminEmail);
        admin.setPassword(this.passwordEncoder.encode(adminPassword));
        admin.setUsername("Administrator");
        admin.setRole(adminRole);
        this.accountRepository.save(admin);

        log.info(">>> Created default admin account {} - change its password after the first login", adminEmail);
    }
}
