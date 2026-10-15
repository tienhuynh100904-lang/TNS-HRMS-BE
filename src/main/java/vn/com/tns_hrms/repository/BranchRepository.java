package vn.com.tns_hrms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.Branch;

public interface BranchRepository extends JpaRepository<Branch, String> {
}
