package vn.com.tns_hrms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** By default (nothing enabled) the application never contacts a device, not even once at startup. */
@SpringBootTest
@ActiveProfiles("test")
class PunchPullJobDisabledTest {
    @MockitoBean
    PunchPullService pullService;

    @Autowired
    ApplicationContext context;

    @Test
    void noJobAndNoPullByDefault() {
        assertEquals(0, context.getBeansOfType(PunchPullJob.class).size());
        verify(pullService, never()).pullAll();
    }
}
