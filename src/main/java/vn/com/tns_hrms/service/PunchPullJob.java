package vn.com.tns_hrms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.config.AttendancePullProperties;

/**
 * Runs the pull every night (app.attendance.pull.cron, default 01:00). Only exists when
 * app.attendance.pull.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "app.attendance.pull.enabled", havingValue = "true")
public class PunchPullJob {
    private static final Logger log = LoggerFactory.getLogger(PunchPullJob.class);

    private final PunchPullService pullService;
    private final AttendancePullProperties properties;

    public PunchPullJob(PunchPullService pullService, AttendancePullProperties properties) {
        this.pullService = pullService;
        this.properties = properties;
    }

    /** One extra pull right after startup when app.attendance.pull.run-on-startup=true, to test without waiting. */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        // the line that tells which mode this run is in: no such line in the log means the pull is off
        log.info("Attendance pull is ON: nightly at cron '{}', also right after startup: {}, devices: {}",
                properties.getCron(), properties.isRunOnStartup(),
                properties.getDevices().stream().map(d -> d.getBranchCode() + "@" + d.getIp()).toList());
        if (properties.isRunOnStartup()) {
            pullService.pullAll();
        }
    }

    @Scheduled(cron = "${app.attendance.pull.cron:0 0 1 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        pullService.pullAll();
    }
}
