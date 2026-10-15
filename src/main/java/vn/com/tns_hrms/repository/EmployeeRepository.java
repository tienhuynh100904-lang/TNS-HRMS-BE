package vn.com.tns_hrms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
