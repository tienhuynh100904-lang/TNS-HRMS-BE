package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.com.tns_hrms.config.AttendancePullProperties;
import vn.com.tns_hrms.config.AttendancePullProperties.DeviceConfig;
import vn.com.tns_hrms.repository.PermissionRepository;
import vn.com.tns_hrms.repository.PunchRawRepository;

/** POST /api/v1/attendance/pull against a fake device script (src/test/resources/fake_pull.py). */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PunchPullApiIntegrationTest {
    private static final String URL = "/api/v1/attendance/pull";
    private static final String HEADER = "uid,user_id,punch_time,verify,punch,tail_hex\n";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AttendancePullProperties properties;
    @Autowired
    PunchRawRepository punchRawRepository;
    @Autowired
    PermissionRepository permissionRepository;

    @TempDir
    Path dir;

    private String oldScriptDir, oldOutputDir;
    private List<DeviceConfig> oldDevices;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(pythonAvailable(), "python is not on the PATH");
        punchRawRepository.deleteAll();
        oldScriptDir = properties.getScriptDir();
        oldOutputDir = properties.getOutputDir();
        oldDevices = properties.getDevices();

        Files.copy(Path.of("src/test/resources/fake_pull.py"), dir.resolve("fake_pull.py"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(dir.resolve("fake_data.csv"), HEADER + "1,7,2026-01-01 07:30:00,15,255,00\n"
                + "2,7,2026-01-01 17:05:00,15,255,00\n3,8,2026-01-01 07:31:00,15,255,00\n", StandardCharsets.UTF_8);
        properties.setScriptDir(dir.toString());
        properties.setOutputDir(dir.resolve("out").toString());
        properties.setDevices(List.of(device("PKD_ND", "10.0.0.1")));

        token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin@12345\"}")).andReturn().getResponse()
                .getContentAsString()).get("data").get("access_token").asString();
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

    private static DeviceConfig device(String branch, String ip) {
        DeviceConfig d = new DeviceConfig();
        d.setBranchCode(branch);
        d.setIp(ip);
        d.setScript("fake_pull.py");
        return d;
    }

    private MvcResult pull(String query) throws Exception {
        return mockMvc.perform(post(URL + query).header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode data(MvcResult result) throws IOException {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Test
    void pullsNowStoresThePunchesAndPullingAgainAddsNothing() throws Exception {
        MvcResult first = pull("");
        assertEquals(200, first.getResponse().getStatus());
        JsonNode device = data(first).get(0);
        assertEquals("PKD_ND", device.get("branchCode").asString());
        assertEquals(3, device.get("result").get("received").asInt());
        assertEquals(3, device.get("result").get("inserted").asInt());
        assertTrue(device.get("error").isNull());
        assertEquals(3, punchRawRepository.count());

        JsonNode again = data(pull("?branchCode=PKD_ND")).get(0);
        assertEquals(0, again.get("result").get("inserted").asInt());
        assertEquals(3, again.get("result").get("duplicates").asInt());
        assertEquals(3, punchRawRepository.count());
    }

    @Test
    void aFailingDeviceIsReportedInItsEntryAndTheOthersStillRun() throws Exception {
        properties.setDevices(List.of(device("PKD_XK", "fail"), device("PKD_ND", "10.0.0.1")));

        MvcResult result = pull("");

        assertEquals(200, result.getResponse().getStatus());
        JsonNode broken = data(result).get(0);
        assertEquals("PKD_XK", broken.get("branchCode").asString());
        assertTrue(broken.get("result").isNull());
        assertTrue(broken.get("error").asString().contains("exit code 1"));
        assertEquals(3, data(result).get(1).get("result").get("inserted").asInt());
    }

    @Test
    void anUnknownBranchOrNoDeviceIsA404() throws Exception {
        assertEquals(404, pull("?branchCode=NOPE").getResponse().getStatus());
        properties.setDevices(List.of());
        assertEquals(404, pull("").getResponse().getStatus());
    }

    @Test
    void aSecondPullWhileOneIsRunningIsA409() throws Exception {
        Files.writeString(dir.resolve("fake_sleep.txt"), "4"); // the device answers slowly
        CompletableFuture<MvcResult> slow = CompletableFuture.supplyAsync(() -> {
            try {
                return pull("");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(1500);

        assertEquals(409, pull("").getResponse().getStatus());
        assertEquals(200, slow.get().getResponse().getStatus());
        // the lock is free again afterwards
        Files.delete(dir.resolve("fake_sleep.txt"));
        assertEquals(200, pull("").getResponse().getStatus());
    }

    @Test
    void needsLoginAndIsRegisteredAsAPermission() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
        assertTrue(permissionRepository.findByApiPathAndMethod(URL, "POST").isPresent());
    }
}
