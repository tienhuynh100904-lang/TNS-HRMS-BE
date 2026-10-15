package vn.com.tns_hrms.service;

import java.time.LocalDateTime;
import java.util.List;

import vn.com.tns_hrms.config.AttendancePullProperties.DeviceConfig;
import vn.com.tns_hrms.dto.request.ReqPunch;

/**
 * Reads the punches of one device. How it talks to the device (Python script, pure Java, ...) is up to the
 * implementation.
 */
public interface PunchPuller {
    /**
     * @param since only punches made at or after this time are needed; null for everything the device holds
     * @throws IllegalStateException when the device cannot be read
     */
    List<ReqPunch> pull(DeviceConfig device, LocalDateTime since);
}
