package vn.com.tns_hrms.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.com.tns_hrms.config.AttendancePullProperties;
import vn.com.tns_hrms.config.AttendancePullProperties.DeviceConfig;
import vn.com.tns_hrms.dto.request.ReqPunch;
import vn.com.tns_hrms.dto.response.ResImportResult;
import vn.com.tns_hrms.repository.PunchRawRepository;

/**
 * Pulls the newest punches of the configured devices and stores them; see PunchImportService for the duplicate
 * rules.
 */
@Service
public class PunchPullService {
    private static final Logger log = LoggerFactory.getLogger(PunchPullService.class);

    private final AttendancePullProperties properties;
    private final PunchPuller puller;
    private final PunchImportService importService;
    private final PunchRawRepository punchRawRepository;

    public PunchPullService(AttendancePullProperties properties, PunchPuller puller,
            PunchImportService importService, PunchRawRepository punchRawRepository) {
        this.properties = properties;
        this.puller = puller;
        this.importService = importService;
        this.punchRawRepository = punchRawRepository;
    }

    /** Pulls every configured device. A device that fails is logged and does not stop the others. */
    public void pullAll() {
        for (DeviceConfig device : properties.getDevices()) {
            try {
                pull(device);
            } catch (RuntimeException e) {
                log.error("Pulling punches of {} failed: {}", device.getBranchCode(), e.getMessage(), e);
            }
        }
    }

    public ResImportResult pull(DeviceConfig device) {
        // everything older than the newest stored punch (minus the overlap) is already in the database
        LocalDateTime since = punchRawRepository.findLastPunchTime(device.getBranchCode())
                .map(last -> last.minusHours(properties.getOverlapHours())).orElse(null);
        List<ReqPunch> punches = puller.pull(device, since);
        return importService.importPunches(device.getBranchCode(), punches);
    }
}
