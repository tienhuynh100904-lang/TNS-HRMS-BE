package vn.com.tns_hrms.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUserCode(String userCode);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByUserCode(String userCode);
}
