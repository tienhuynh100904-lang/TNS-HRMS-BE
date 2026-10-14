package vn.com.tns_hrms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import vn.com.tns_hrms.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {
    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsById(Long id);

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsByName(String name);

    long countByActive(boolean active);
}
