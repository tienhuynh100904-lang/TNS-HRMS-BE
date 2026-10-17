package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.com.tns_hrms.entity.Department;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.entity.PunchRaw;
import vn.com.tns_hrms.repository.DepartmentRepository;
import vn.com.tns_hrms.repository.EmployeeRepository;
import vn.com.tns_hrms.repository.PermissionRepository;
import vn.com.tns_hrms.repository.PunchRawRepository;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AttendanceApiIntegrationTest {
    private static final String DAY = "2026-10-14";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PunchRawRepository punchRawRepository;
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    PermissionRepository permissionRepository;

    String token;

    @BeforeEach
    void setUp() throws Exception {
        punchRawRepository.deleteAll();
        employeeRepository.deleteAll();

        Department department = departmentRepository.findAll().stream().filter(d -> d.getCode().equals("TST_D"))
                .findFirst().orElseGet(() -> {
                    Department d = new Department();
                    d.setCode("TST_D");
                    d.setName("Phòng thử nghiệm");
                    return departmentRepository.save(d);
                });
        Employee nguyen = new Employee();
        nguyen.setEmployeeCode("TNS0007");
        nguyen.setUserCode("7");
        nguyen.setFullName("Nguyễn Văn A");
        nguyen.setDepartment(department);
        employeeRepository.save(nguyen);

        // user 7: three punches on the 14th (07:30 .. 17:05) and one on the 15th
        punch("PKD_ND", "7", "2026-10-14T07:30:00");
        punch("PKD_ND", "7", "2026-10-14T11:50:00");
        punch("PKD_ND", "7", "2026-10-14T17:05:00");
        punch("PKD_ND", "7", "2026-10-15T07:29:00");
        // user 8: one punch; user 9: two punches a minute apart (the same swipe twice); nobody owns 8 and 9
        punch("PKD_ND", "8", "2026-10-14T07:31:00");
        punch("PKD_ND", "9", "2026-10-14T07:40:00");
        punch("PKD_ND", "9", "2026-10-14T07:41:00");
        // user 10 at another branch
        punch("PKD_XK", "10", "2026-10-14T07:00:00");
        punch("PKD_XK", "10", "2026-10-14T16:00:00");

        token = adminToken();
    }

    private void punch(String branch, String userCode, String time) {
        PunchRaw p = new PunchRaw();
        p.setBranchCode(branch);
        p.setUserCode(userCode);
        p.setPunchTime(LocalDateTime.parse(time));
        punchRawRepository.save(p);
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin@12345\"}")).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("access_token")
                .asString();
    }

    private JsonNode call(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static JsonNode find(JsonNode data, String userCode) {
        for (JsonNode row : data.get("result")) {
            if (row.get("userCode").asString().equals(userCode)) {
                return row;
            }
        }
        throw new AssertionError("no row for user " + userCode);
    }

    // ---- the punches of one day

    private static JsonNode punchAt(JsonNode data, String userCode, String time) {
        for (JsonNode row : data.get("result")) {
            if (row.get("userCode").asString().equals(userCode) && row.get("punchTime").asString().equals(time)) {
                return row;
            }
        }
        throw new AssertionError("no punch of user " + userCode + " at " + time);
    }

    @Test
    void listsEveryPunchOfTheDayWithOneTimeAndTheEmployee() throws Exception {
        JsonNode data = call("/api/v1/attendance/users?date=" + DAY);

        // 3 (user 7) + 1 (8) + 2 (9) + 2 (10); the punch of the 15th is not part of this day
        assertEquals(8, data.get("meta").get("total").asInt());
        JsonNode first = punchAt(data, "7", "2026-10-14T07:30:00");
        assertEquals("PKD_ND", first.get("branchCode").asString());
        assertEquals("TNS0007", first.get("employeeCode").asString());
        assertEquals("Nguyễn Văn A", first.get("fullName").asString());
        assertEquals("Phòng thử nghiệm", first.get("departmentName").asString());
        // a row is one punch: a single time, no totals
        assertTrue(first.has("punchTime"));
        assertTrue(!first.has("punchCount") && !first.has("firstPunchTime") && !first.has("lastPunchTime"));
        // nobody owns user code 8 yet
        assertTrue(punchAt(data, "8", "2026-10-14T07:31:00").get("employeeCode").isNull());
    }

    @Test
    void filtersTheDayByBranchAndKeyword() throws Exception {
        assertEquals(6, call("/api/v1/attendance/users?date=" + DAY + "&branchCode=PKD_ND").get("meta").get("total")
                .asInt());
        // name, case insensitive and with the accents typed: the 3 punches of user 7
        assertEquals(3, call("/api/v1/attendance/users?date=" + DAY + "&keyword=nguyễn").get("meta").get("total")
                .asInt());
        assertEquals(3, call("/api/v1/attendance/users?date=" + DAY + "&keyword=TNS0007").get("meta").get("total")
                .asInt());
        assertEquals(1, call("/api/v1/attendance/users?date=" + DAY + "&keyword=8").get("meta").get("total").asInt());
        // the 15th has a single punch
        JsonNode fifteenth = call("/api/v1/attendance/users?date=2026-10-15");
        assertEquals(1, fifteenth.get("meta").get("total").asInt());
        assertEquals("2026-10-15T07:29:00", fifteenth.get("result").get(0).get("punchTime").asString());
        // a % typed in the search is text, not "match everything"
        assertEquals(0, call("/api/v1/attendance/users?date=" + DAY + "&keyword=%25").get("meta").get("total")
                .asInt());
    }

    @Test
    void pagesThePunchesOldestFirst() throws Exception {
        JsonNode first = call("/api/v1/attendance/users?date=" + DAY + "&size=3&page=0");
        assertEquals(8, first.get("meta").get("total").asInt());
        assertEquals(3, first.get("meta").get("pages").asInt());
        assertEquals(1, first.get("meta").get("page").asInt());
        assertEquals(3, first.get("result").size());
        assertEquals("2026-10-14T07:00:00", first.get("result").get(0).get("punchTime").asString());
        assertEquals("2026-10-14T07:30:00", first.get("result").get(1).get("punchTime").asString());
        assertEquals("2026-10-14T07:31:00", first.get("result").get(2).get("punchTime").asString());

        JsonNode last = call("/api/v1/attendance/users?date=" + DAY + "&size=3&page=2");
        assertEquals(2, last.get("result").size());
        assertEquals("2026-10-14T17:05:00", last.get("result").get(1).get("punchTime").asString());
    }

    // ---- attendance of each person on one day

    @Test
    void dailyAttendanceGivesCheckInAndCheckOutOfEachPerson() throws Exception {
        JsonNode data = call("/api/v1/attendance/daily?date=" + DAY);

        assertEquals(4, data.get("meta").get("total").asInt());
        JsonNode seven = find(data, "7");
        assertEquals(DAY, seven.get("workDate").asString());
        assertEquals("2026-10-14T07:30:00", seven.get("checkInTime").asString());
        assertEquals("2026-10-14T17:05:00", seven.get("checkOutTime").asString());
        assertEquals(3, seven.get("punchCount").asInt()); // the punch of the 15th is not part of this day
        assertEquals("OK", seven.get("status").asString());
        assertEquals("Nguyễn Văn A", seven.get("fullName").asString());

        JsonNode eight = find(data, "8");
        assertEquals("2026-10-14T07:31:00", eight.get("checkInTime").asString());
        assertTrue(eight.get("checkOutTime").isNull());
        assertEquals("SINGLE_PUNCH", eight.get("status").asString());

        // 07:40 and 07:41 are the same swipe seen twice: no check-out
        JsonNode nine = find(data, "9");
        assertEquals(2, nine.get("punchCount").asInt());
        assertTrue(nine.get("checkOutTime").isNull());
        assertEquals("SINGLE_PUNCH", nine.get("status").asString());

        assertEquals("OK", find(data, "10").get("status").asString());
    }

    @Test
    void dailyAttendanceFiltersByBranchAndPages() throws Exception {
        assertEquals(1, call("/api/v1/attendance/daily?date=" + DAY + "&branchCode=PKD_XK").get("meta").get("total")
                .asInt());
        JsonNode page = call("/api/v1/attendance/daily?date=" + DAY + "&size=2&page=1");
        assertEquals(2, page.get("result").size());
        assertEquals(2, page.get("meta").get("pages").asInt());
        // a day nobody punched
        assertEquals(0, call("/api/v1/attendance/daily?date=2026-01-01").get("meta").get("total").asInt());
    }

    @Test
    void aSinglePunchTodayIsStillInProgress() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withNano(0);
        punch("PKD_ND", "55", now.toString());

        JsonNode data = call("/api/v1/attendance/daily?date=" + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));

        assertEquals("IN_PROGRESS", find(data, "55").get("status").asString());
    }

    // ---- bad requests and security

    @Test
    void badParametersAreRejectedWithA400() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()); // date missing
        mockMvc.perform(get("/api/v1/attendance/daily?date=abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/attendance/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()); // date missing
        mockMvc.perform(get("/api/v1/attendance/users?date=2026-13-45").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointsNeedLoginAndAreRegisteredAsPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/attendance/users?date=" + DAY)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/attendance/daily?date=" + DAY)).andExpect(status().isUnauthorized());

        assertTrue(permissionRepository.findByApiPathAndMethod("/api/v1/attendance/users", "GET").isPresent());
        assertTrue(permissionRepository.findByApiPathAndMethod("/api/v1/attendance/daily", "GET").isPresent());
    }
}
