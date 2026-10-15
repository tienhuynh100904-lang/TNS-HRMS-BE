package vn.com.tns_hrms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
