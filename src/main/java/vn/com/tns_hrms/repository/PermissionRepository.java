package vn.com.tns_hrms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import vn.com.tns_hrms.entity.Permission;

public interface PermissionRepository
        extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {
    Optional<Permission> findByNameAndApiPathAndMethod(String name, String apiPath, String method);

    Optional<Permission> findByApiPathAndMethod(String apiPath, String method);

    boolean existsByApiPathAndMethod(String apiPath, String method);

    long countByActive(boolean active);

    @Query("SELECT COUNT(DISTINCT p.module) FROM Permission p WHERE p.module IS NOT NULL")
    long countDistinctModule();

    @Query("SELECT DISTINCT p.module FROM Permission p WHERE p.module IS NOT NULL")
    List<String> findDistinctModules();
}
