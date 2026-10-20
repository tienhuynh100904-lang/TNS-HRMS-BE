package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.com.tns_hrms.config.RequestLogFilter;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RequestLogFilterTest {
    @Autowired
    MockMvc mockMvc;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void capture() {
        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsTheCallAndItsAnswerEvenWhenSecurityRejectsIt() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/daily?date=2026-10-14&keyword=an"));

        assertEquals(2, lines().size());
        assertTrue(lines().get(0).startsWith("API call   GET /api/v1/attendance/daily?date=2026-10-14&keyword=an from "),
                lines().get(0));
        assertTrue(lines().get(1).startsWith("API answer GET /api/v1/attendance/daily?date=2026-10-14&keyword=an -> 401 in "),
                lines().get(1));
    }

    @Test
    void neverLogsTheTokenAndSkipsOptionsCalls() throws Exception {
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer secret-token-123"));
        mockMvc.perform(options("/api/v1/clients"));

        assertFalse(lines().stream().anyMatch(l -> l.contains("secret-token-123")));
        assertEquals(2, lines().size()); // only the GET, not the OPTIONS
    }
}
