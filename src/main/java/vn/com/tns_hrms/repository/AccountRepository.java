package vn.com.tns_hrms.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import vn.com.tns_hrms.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {
    @EntityGraph(attributePaths = { "role", "role.permissions" })
    Account findByEmail(String email);

    long countByRole_Name(String roleName);

    long countByActive(boolean active);
}
