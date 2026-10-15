package vn.com.tns_hrms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import vn.com.tns_hrms.config.AttendancePullProperties;
import vn.com.tns_hrms.config.AttendancePullProperties.DeviceConfig;
import vn.com.tns_hrms.dto.response.ResImportResult;
import vn.com.tns_hrms.repository.PunchRawRepository;

/** Runs the real PythonPunchPuller against a fake script (src/test/resources/fake_pull.py), no device involved. */
@SpringBootTest
@ActiveProfiles("test")
class PunchPullServiceTest {
    private static final String HEADER = "uid,user_id,punch_time,verify,punch,tail_hex\n";

    @Autowired
    PunchPullService pullService;
    @Autowired
    AttendancePullProperties properties;
    @Autowired
    PunchRawRepository punchRawRepository;

    @TempDir
    Path dir;

    private String oldScriptDir, oldOutputDir;
    private List<DeviceConfig> oldDevices;
    private DeviceConfig device;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(pythonAvailable(), "python is not on the PATH");
        punchRawRepository.deleteAll();
        oldScriptDir = properties.getScriptDir();
        oldOutputDir = properties.getOutputDir();
        oldDevices = properties.getDevices();

        Files.copy(Path.of("src/test/resources/fake_pull.py"), dir.resolve("fake_pull.py"),
                StandardCopyOption.REPLACE_EXISTING);
        properties.setScriptDir(dir.toString());
        properties.setOutputDir(dir.resolve("out").toString());
        properties.setOverlapHours(48);

        device = new DeviceConfig();
        device.setBranchCode("PKD_ND");
        device.setIp("10.0.0.1");
        device.setScript("fake_pull.py");
        properties.setDevices(List.of(device));
    }

    @AfterEach
    void tearDown() {
        if (oldScriptDir != null) {
            properties.setScriptDir(oldScriptDir);
            properties.setOutputDir(oldOutputDir);
            properties.setDevices(oldDevices);
        }
    }

    private static boolean pythonAvailable() {
        try {
            return new ProcessBuilder("python", "--version").redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void deviceHolds(String... lines) throws IOException {
        Files.writeString(dir.resolve("fake_data.csv"), HEADER + String.join("\n", lines) + "\n",
                StandardCharsets.UTF_8);
    }

    @Test
    void firstPullTakesEverythingThenOnlyTheNewestPartIsReadAndNothingIsDuplicated() throws IOException {
        deviceHolds(
                "1,7,2026-01-01 07:30:00,15,255,00",
                "2,7,2026-01-10 07:30:00,15,255,00",
                "3,8,2026-01-11 07:30:00,15,255,00");

        ResImportResult first = pullService.pull(device);
        assertEquals(3, first.getReceived());
        assertEquals(3, first.getInserted());

        // the newest stored punch is 01-11 07:30, so the next pull asks for 01-09 07:30 onwards: the 01-01 punch
        // is never read again, the two others are read and dropped as duplicates
        ResImportResult second = pullService.pull(device);
        assertEquals(2, second.getReceived());
        assertEquals(0, second.getInserted());
        assertEquals(2, second.getDuplicates());
        assertEquals(3, punchRawRepository.count());

        // new punches arrive on the device (which still returns its whole log)
        deviceHolds(
                "1,7,2026-01-01 07:30:00,15,255,00",
                "2,7,2026-01-10 07:30:00,15,255,00",
                "3,8,2026-01-11 07:30:00,15,255,00",
                "4,7,2026-01-12 07:31:00,15,255,00",
                "5,8,2026-01-12 17:02:00,15,255,00",
                "6,9,RAW:abcdef,15,255,00");
        ResImportResult third = pullService.pull(device);
        assertEquals(2, third.getInserted());
        assertEquals(5, punchRawRepository.count());
        assertTrue(punchRawRepository.findAll().stream().allMatch(p -> p.getBranchCode().equals("PKD_ND")));
    }

    @Test
    void aFailingDeviceIsReportedAndDoesNotStopTheOthers() throws IOException {
        deviceHolds("1,7,2026-01-01 07:30:00,15,255,00");
        DeviceConfig broken = new DeviceConfig();
        broken.setBranchCode("PKD_XK");
        broken.setIp("fail");
        broken.setScript("fake_pull.py");
        properties.setDevices(List.of(broken, device));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pullService.pull(broken));
        assertTrue(e.getMessage().contains("exit code 1"), e.getMessage());
        assertTrue(e.getMessage().contains("ConnectionError"), e.getMessage());

        pullService.pullAll(); // must not throw, and still pulls the working device
        assertEquals(1, punchRawRepository.count());
    }

    @Test
    void aMissingScriptIsReportedClearly() {
        device.setScript("nope.py");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pullService.pull(device));
        assertTrue(e.getMessage().contains("Python script not found"));
    }
}
