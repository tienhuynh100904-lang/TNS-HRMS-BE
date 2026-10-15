package vn.com.tns_hrms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.com.tns_hrms.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, Long> {
}
