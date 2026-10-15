package vn.com.tns_hrms.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the pull every night (app.attendance.pull.cron, default 01:00). Only exists when
 * app.attendance.pull.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "app.attendance.pull.enabled", havingValue = "true")
public class PunchPullJob {
    private final PunchPullService pullService;

    public PunchPullJob(PunchPullService pullService) {
        this.pullService = pullService;
    }

    @Scheduled(cron = "${app.attendance.pull.cron:0 0 1 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        pullService.pullAll();
    }
}
