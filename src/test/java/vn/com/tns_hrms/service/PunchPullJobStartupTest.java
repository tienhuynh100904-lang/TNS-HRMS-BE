package vn.com.tns_hrms.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** With run-on-startup the pull must start by itself once the application is ready, without waiting for the cron. */
@SpringBootTest(properties = { "app.attendance.pull.enabled=true", "app.attendance.pull.run-on-startup=true",
        // far away from now, so only the startup trigger can fire during the test
        "app.attendance.pull.cron=0 0 1 1 1 *" })
@ActiveProfiles("test")
class PunchPullJobStartupTest {
    @MockitoBean
    PunchPullService pullService;

    @Autowired
    PunchPullJob job;

    @Test
    void pullsOnceRightAfterStartup() {
        verify(pullService, times(1)).pullAll();
    }
}
