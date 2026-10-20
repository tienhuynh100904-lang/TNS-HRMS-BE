package vn.com.tns_hrms.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.request.ReqClient;
import vn.com.tns_hrms.dto.response.ResClient;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.entity.ApiClient;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.exception.BadRequestException;
import vn.com.tns_hrms.exception.ResourceAlreadyExistsException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.mapper.PaginationMapper;
import vn.com.tns_hrms.repository.ApiClientRepository;
import vn.com.tns_hrms.repository.RoleRepository;

@Service
public class ApiClientService {
    private static final int SECRET_BYTES = 32;

    private final ApiClientRepository clientRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiClientService(ApiClientRepository clientRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ResultPagination getAllClientsWithPagination(Pageable pageable) {
        Page<ApiClient> page = clientRepository.findAll(pageable);
        return PaginationMapper.toPaginationDTO(page, page.getContent().stream().map(c -> toDto(c, new ResClient())).toList());
    }

    @Transactional(readOnly = true)
    public ResClient getClientDetail(long id) {
        return toDto(find(id), new ResClient());
    }

    /** Creates the client with a generated secret, which is returned here and never again. */
    @Transactional
    public ResClient.WithSecret createClient(ReqClient request) {
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            throw new BadRequestException("Client id is required");
        }
        String clientId = request.getClientId().trim();
        if (clientRepository.existsByClientId(clientId)) {
            throw new ResourceAlreadyExistsException("Client with id " + clientId + " already exists");
        }
        Role role = activeRole(request.getRoleId());

        String secret = generateSecret();
        ApiClient client = new ApiClient();
        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(secret));
        client.setName(request.getName());
        client.setDescription(request.getDescription());
        client.setActive(request.isActive());
        client.setRole(role);

        ResClient.WithSecret result = toDto(clientRepository.save(client), new ResClient.WithSecret());
        result.setClientSecret(secret);
        return result;
    }

    @Transactional
    public ResClient updateClient(ReqClient request) {
        ApiClient client = find(request.getId());
        Role role = activeRole(request.getRoleId());

        // the last active ADMIN client must stay: nobody could manage the other clients any more
        boolean staysActiveAdmin = Role.ADMIN.equals(role.getName()) && request.isActive();
        if (isActiveAdmin(client) && !staysActiveAdmin
                && clientRepository.countByRole_NameAndActive(Role.ADMIN, true) <= 1) {
            throw new BadRequestException("The last active ADMIN client cannot be deactivated or lose the ADMIN role");
        }

        client.setName(request.getName());
        client.setDescription(request.getDescription());
        client.setActive(request.isActive());
        client.setRole(role);
        return toDto(clientRepository.save(client), new ResClient());
    }

    /** Replaces the secret with a new generated one, shown here once. The old secret stops working at once. */
    @Transactional
    public ResClient.WithSecret rotateSecret(long id) {
        ApiClient client = find(id);
        String secret = generateSecret();
        client.setClientSecret(passwordEncoder.encode(secret));

        ResClient.WithSecret result = toDto(clientRepository.save(client), new ResClient.WithSecret());
        result.setClientSecret(secret);
        return result;
    }

    @Transactional(readOnly = true)
    public ApiClient getByClientId(String clientId) {
        return clientRepository.findByClientId(clientId).orElse(null);
    }

    private ApiClient find(long id) {
        return clientRepository.findWithRoleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
    }

    private Role activeRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));
        if (!role.isActive()) {
            throw new BadRequestException("Cannot assign an inactive role");
        }
        return role;
    }

    private static boolean isActiveAdmin(ApiClient client) {
        return client.isActive() && client.getRole() != null && Role.ADMIN.equals(client.getRole().getName());
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static <T extends ResClient> T toDto(ApiClient client, T dto) {
        dto.setId(client.getId());
        dto.setClientId(client.getClientId());
        dto.setName(client.getName());
        dto.setDescription(client.getDescription());
        dto.setActive(client.isActive());
        dto.setRole(client.getRole() == null ? null : client.getRole().getName());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setUpdatedAt(client.getUpdatedAt());
        dto.setCreatedBy(client.getCreatedBy());
        dto.setUpdatedBy(client.getUpdatedBy());
        return dto;
    }
}
