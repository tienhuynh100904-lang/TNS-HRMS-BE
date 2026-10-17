package vn.com.tns_hrms.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import vn.com.tns_hrms.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {
    // the list shows the department of every employee: load them together, not one query per employee
    @Override
    @EntityGraph(attributePaths = "department")
    Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

    Optional<Employee> findByUserCode(String userCode);

    Optional<Employee> findByEmployeeCode(String employeeCode);
}
