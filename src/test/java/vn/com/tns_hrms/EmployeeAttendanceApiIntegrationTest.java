package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
class EmployeeAttendanceApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    PunchRawRepository punchRawRepository;
    @Autowired
    PermissionRepository permissionRepository;

    String token;
    Employee a; // user code 7, has a department
    Employee b; // user code 8, inactive, no department
    Employee c; // no user code (does not use a device)

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
        a = employee("TNS0007", "7", "Nguyễn Văn A", department, true);
        b = employee("TNS0008", "8", "Trần Thị B", null, false);
        c = employee("TNS0009", null, "Lê Văn C", department, true);

        // employee A: 10 punches over three months
        punch("7", "2026-10-01T07:30:00");
        punch("7", "2026-10-01T17:00:00");
        punch("7", "2026-10-02T07:31:00"); // single punch
        punch("7", "2026-10-03T07:30:00"); // the same swipe seen twice
        punch("7", "2026-10-03T07:31:00");
        punch("7", "2026-10-05T08:00:00");
        punch("7", "2026-10-05T12:00:00");
        punch("7", "2026-10-05T17:00:00");
        punch("7", "2026-09-30T23:59:00"); // other months do not count
        punch("7", "2026-11-01T00:00:00");
        punch("8", "2026-10-01T07:00:00"); // another person

        token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"Admin@12345\"}")).andReturn().getResponse()
                .getContentAsString()).get("data").get("access_token").asString();
    }

    private Employee employee(String code, String userCode, String name, Department department, boolean active) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setUserCode(userCode);
        e.setFullName(name);
        e.setDepartment(department);
        e.setActive(active);
        return employeeRepository.save(e);
    }

    private void punch(String userCode, String time) {
        PunchRaw p = new PunchRaw();
        p.setBranchCode("PKD_ND");
        p.setUserCode(userCode);
        p.setPunchTime(LocalDateTime.parse(time));
        punchRawRepository.save(p);
    }

    private MvcResult call(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode data(String url) throws Exception {
        MvcResult result = call(url);
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    // ---- employee list

    @Test
    void listsEmployeesPagedWithTheirDepartment() throws Exception {
        JsonNode all = data("/api/v1/employees?sort=employeeCode,asc");
        assertEquals(3, all.get("meta").get("total").asInt());
        JsonNode first = all.get("result").get(0);
        assertEquals("TNS0007", first.get("employeeCode").asString());
        assertEquals("7", first.get("userCode").asString());
        assertEquals("Nguyễn Văn A", first.get("fullName").asString());
        assertEquals("Phòng thử nghiệm", first.get("departmentName").asString());
        assertTrue(first.get("active").asBoolean());
        // no department, no user code: the fields are null, the employee is still listed
        assertTrue(all.get("result").get(1).get("departmentName").isNull());
        assertTrue(all.get("result").get(2).get("userCode").isNull());

        JsonNode page = data("/api/v1/employees?sort=employeeCode,asc&size=2&page=1");
        assertEquals(2, page.get("meta").get("pages").asInt());
        assertEquals(1, page.get("result").size());
        assertEquals("TNS0009", page.get("result").get(0).get("employeeCode").asString());
    }

    @Test
    void filtersEmployees() throws Exception {
        MvcResult inactive = mockMvc.perform(get("/api/v1/employees").param("filter", "active:false")
                .header("Authorization", "Bearer " + token)).andReturn();
        JsonNode data = objectMapper.readTree(inactive.getResponse().getContentAsString()).get("data");
        assertEquals(1, data.get("meta").get("total").asInt());
        assertEquals("TNS0008", data.get("result").get(0).get("employeeCode").asString());

        MvcResult byName = mockMvc.perform(get("/api/v1/employees").param("filter", "fullName ~ '*Văn*'")
                .header("Authorization", "Bearer " + token)).andReturn();
        assertEquals(2, objectMapper.readTree(byName.getResponse().getContentAsString()).get("data").get("meta")
                .get("total").asInt());
    }

    // ---- the punches of one employee

    private static String time(JsonNode data, int i) {
        return data.get("result").get(i).get("punchTime").asString();
    }

    @Test
    void listsAllThePunchesOfOnePersonOldestFirstByDefault() throws Exception {
        JsonNode data = data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&size=100");

        // every punch of user code 7, from every month; nothing of another person
        assertEquals(10, data.get("meta").get("total").asInt());
        assertEquals("2026-09-30T23:59:00", time(data, 0));
        assertEquals("2026-10-01T07:30:00", time(data, 1));
        assertEquals("2026-11-01T00:00:00", time(data, 9));
        assertEquals("PKD_ND", data.get("result").get(0).get("branchCode").asString());
        assertTrue(data.get("result").get(0).get("direction").isNull());
    }

    @Test
    void sortsByTimeInEitherDirection() throws Exception {
        JsonNode newest = data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&sort=punchTime,desc&size=100");
        assertEquals("2026-11-01T00:00:00", time(newest, 0));
        assertEquals("2026-10-05T17:00:00", time(newest, 1));
        assertEquals("2026-09-30T23:59:00", time(newest, 9));

        JsonNode oldest = data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&sort=punchTime,asc&size=100");
        assertEquals("2026-09-30T23:59:00", time(oldest, 0));
    }

    @Test
    void filtersByPeriodWithBothDaysIncluded() throws Exception {
        // the whole of October: 8 punches, not the ones at 23:59 on 30 September and 00:00 on 1 November
        JsonNode october = data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&from=2026-10-01&to=2026-10-31&size=100");
        assertEquals(8, october.get("meta").get("total").asInt());
        assertEquals("2026-10-01T07:30:00", time(october, 0));
        assertEquals("2026-10-05T17:00:00", time(october, 7));

        assertEquals(2, data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&from=2026-10-03&to=2026-10-03")
                .get("meta").get("total").asInt());
        // only a lower or only an upper limit
        assertEquals(1, data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&from=2026-11-01").get("meta")
                .get("total").asInt());
        assertEquals(1, data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&to=2026-09-30").get("meta")
                .get("total").asInt());
    }

    @Test
    void pagesThePunches() throws Exception {
        JsonNode second = data("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&size=4&page=1");
        assertEquals(10, second.get("meta").get("total").asInt());
        assertEquals(3, second.get("meta").get("pages").asInt());
        assertEquals(4, second.get("result").size());
        assertEquals("2026-10-03T07:30:00", time(second, 0)); // punches 5 to 8 of the oldest-first list
    }

    @Test
    void anEmployeeWithoutADeviceHasNoPunches() throws Exception {
        JsonNode noDevice = data("/api/v1/attendance/monthly?employeeId=" + c.getId());
        assertEquals(0, noDevice.get("meta").get("total").asInt());
        assertEquals(0, noDevice.get("result").size());
    }

    @Test
    void badRequestsAreRejected() throws Exception {
        assertEquals(404, call("/api/v1/attendance/monthly?employeeId=999999").getResponse().getStatus());
        assertEquals(400, call("/api/v1/attendance/monthly").getResponse().getStatus()); // employeeId missing
        assertEquals(400, call("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&from=2026-13-01").getResponse()
                .getStatus());
        assertEquals(400, call("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&from=2026-10-05&to=2026-10-01")
                .getResponse().getStatus());
        assertEquals(400, call("/api/v1/attendance/monthly?employeeId=" + a.getId() + "&sort=branchCode,asc")
                .getResponse().getStatus());
    }

    @Test
    void needLoginAndAreRegisteredAsPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/employees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/attendance/monthly?employeeId=1"))
                .andExpect(status().isUnauthorized());
        assertTrue(permissionRepository.findByApiPathAndMethod("/api/v1/employees", "GET").isPresent());
        assertTrue(permissionRepository.findByApiPathAndMethod("/api/v1/attendance/monthly", "GET").isPresent());
    }
}
