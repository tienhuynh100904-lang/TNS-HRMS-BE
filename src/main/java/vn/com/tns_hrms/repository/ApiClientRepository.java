package vn.com.tns_hrms.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.ApiClient;

public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {
    // role and its permissions are needed on every request by the permission check
    @EntityGraph(attributePaths = { "role", "role.permissions" })
    Optional<ApiClient> findByClientId(String clientId);

    @EntityGraph(attributePaths = "role")
    Optional<ApiClient> findWithRoleById(Long id);

    @Override
    @EntityGraph(attributePaths = "role")
    Page<ApiClient> findAll(Pageable pageable);

    boolean existsByClientId(String clientId);

    long countByActive(boolean active);

    long countByRole_NameAndActive(String roleName, boolean active);
}
