package vn.com.tns_hrms.config.seed;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.entity.ApiClient;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.repository.ApiClientRepository;
import vn.com.tns_hrms.repository.RoleRepository;

/**
 * Creates the first ADMIN client when no client exists yet, so someone can get a token and register the others.
 * The secret comes from app.seed.admin-client-secret; when that is empty a random one is generated and printed once
 * in the log, because there is no other way to see it afterwards.
 */
@Component
public class AdminClientSeeder {
    private static final Logger log = LoggerFactory.getLogger(AdminClientSeeder.class);

    private final ApiClientRepository clientRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-client-id}")
    private String adminClientId;

    @Value("${app.seed.admin-client-secret:}")
    private String adminClientSecret;

    public AdminClientSeeder(ApiClientRepository clientRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void seed() {
        if (this.clientRepository.count() > 0) {
            return;
        }

        Role adminRole = this.roleRepository.findByName(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role must be seeded before the admin client"));

        boolean generated = adminClientSecret == null || adminClientSecret.isBlank();
        String secret = generated ? generateSecret() : adminClientSecret;

        ApiClient admin = new ApiClient();
        admin.setClientId(adminClientId);
        admin.setClientSecret(this.passwordEncoder.encode(secret));
        admin.setName("Administrator");
        admin.setRole(adminRole);
        this.clientRepository.save(admin);

        if (generated) {
            log.warn(">>> Created default admin client {} with generated secret {} - it is shown only now, store it",
                    adminClientId, secret);
        } else {
            log.info(">>> Created default admin client {}", adminClientId);
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
