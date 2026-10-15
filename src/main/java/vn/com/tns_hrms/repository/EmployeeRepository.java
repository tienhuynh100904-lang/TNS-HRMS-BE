package vn.com.tns_hrms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByBranchCode(String branchCode);
}
