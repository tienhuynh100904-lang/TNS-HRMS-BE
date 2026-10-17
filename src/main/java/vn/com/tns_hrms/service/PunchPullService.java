package vn.com.tns_hrms.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.com.tns_hrms.config.AttendancePullProperties;
import vn.com.tns_hrms.config.AttendancePullProperties.DeviceConfig;
import vn.com.tns_hrms.dto.request.ReqPunch;
import vn.com.tns_hrms.dto.response.ResImportResult;
import vn.com.tns_hrms.dto.response.ResPullResult;
import vn.com.tns_hrms.exception.ResourceAlreadyExistsException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
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
    // a device takes one connection at a time, so only one pull runs at once (nightly job and API share this)
    private final ReentrantLock running = new ReentrantLock();

    public PunchPullService(AttendancePullProperties properties, PunchPuller puller,
            PunchImportService importService, PunchRawRepository punchRawRepository) {
        this.properties = properties;
        this.puller = puller;
        this.importService = importService;
        this.punchRawRepository = punchRawRepository;
    }

    /**
     * Pulls every configured device (the nightly job). A device that fails is logged and does not stop the others.
     * When a pull is already running this one is skipped.
     */
    public void pullAll() {
        if (!running.tryLock()) {
            log.warn("A pull is already running, this one is skipped");
            return;
        }
        try {
            pullDevices(properties.getDevices());
        } finally {
            running.unlock();
        }
    }

    /**
     * Pulls right now, on request: the device of one branch, or every configured device when branchCode is null.
     * Each device reports its own counts or its own error.
     *
     * @throws ResourceNotFoundException when no configured device matches
     * @throws ResourceAlreadyExistsException when a pull is already running
     */
    public List<ResPullResult> pullNow(String branchCode) {
        List<DeviceConfig> devices = properties.getDevices().stream()
                .filter(d -> branchCode == null || branchCode.equals(d.getBranchCode())).toList();
        if (devices.isEmpty()) {
            throw new ResourceNotFoundException(branchCode == null ? "No attendance device is configured"
                    : "No attendance device is configured for branch " + branchCode);
        }
        if (!running.tryLock()) {
            throw new ResourceAlreadyExistsException("A pull is already running, try again when it has finished");
        }
        try {
            return pullDevices(devices);
        } finally {
            running.unlock();
        }
    }

    private List<ResPullResult> pullDevices(List<DeviceConfig> devices) {
        return devices.stream().map(device -> {
            try {
                return new ResPullResult(device.getBranchCode(), pull(device), null);
            } catch (RuntimeException e) {
                log.error("Pulling punches of {} failed: {}", device.getBranchCode(), e.getMessage(), e);
                return new ResPullResult(device.getBranchCode(), null, e.getMessage());
            }
        }).toList();
    }

    public ResImportResult pull(DeviceConfig device) {
        // everything older than the newest stored punch (minus the overlap) is already in the database
        LocalDateTime since = punchRawRepository.findLastPunchTime(device.getBranchCode())
                .map(last -> last.minusHours(properties.getOverlapHours())).orElse(null);
        List<ReqPunch> punches = puller.pull(device, since);
        return importService.importPunches(device.getBranchCode(), punches);
    }
}
