package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class EmployeeWriteApiIntegrationTest {
    private static final String URL = "/api/v1/employees";

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
    Department department;
    Employee a;
    Employee b;

    @BeforeEach
    void setUp() throws Exception {
        punchRawRepository.deleteAll();
        employeeRepository.deleteAll();
        department = departmentRepository.findAll().stream().filter(d -> d.getCode().equals("TST_D")).findFirst()
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setCode("TST_D");
                    d.setName("Phòng thử nghiệm");
                    return departmentRepository.save(d);
                });
        a = employee("TNS0001", "1", "Nguyễn Văn A");
        b = employee("TNS0002", "2", "Trần Thị B");
        token = TestAuth.adminToken(mockMvc, objectMapper);
    }

    private Employee employee(String code, String userCode, String name) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setUserCode(userCode);
        e.setFullName(name);
        return employeeRepository.save(e);
    }

    private MvcResult update(String json) throws Exception {
        return mockMvc.perform(put(URL).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private String body(long id, String employeeCode, String userCode, String name, Object departmentId,
            String active) {
        return "{\"id\":" + id + ",\"employeeCode\":" + quote(employeeCode) + ",\"userCode\":" + quote(userCode)
                + ",\"cardCode\":null,\"fullName\":" + quote(name) + ",\"departmentId\":" + departmentId
                + (active == null ? "" : ",\"active\":" + active) + "}";
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    // ---- update

    @Test
    void updatesTheEmployeeAndReturnsTheNewData() throws Exception {
        MvcResult result = update(body(a.getId(), "TNS0100", "100", "Nguyễn Văn An", department.getId(), "false"));

        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals("TNS0100", data.get("employeeCode").asString());
        assertEquals("100", data.get("userCode").asString());
        assertEquals("Nguyễn Văn An", data.get("fullName").asString());
        assertEquals("Phòng thử nghiệm", data.get("departmentName").asString());
        assertEquals(false, data.get("active").asBoolean());

        Employee saved = employeeRepository.findById(a.getId()).orElseThrow();
        assertEquals("100", saved.getUserCode());
        assertEquals(false, saved.isActive());
    }

    @Test
    void whatIsLeftOutOrEmptyIsCleared() throws Exception {
        update(body(a.getId(), "TNS0001", "1", "Nguyễn Văn A", department.getId(), "true"));

        // empty strings and a null department: stored as "nothing", never as "" (that would clash with others)
        MvcResult result = update(body(a.getId(), "  ", "", "Nguyễn Văn A", null, "true"));

        assertEquals(200, result.getResponse().getStatus());
        Employee saved = employeeRepository.findById(a.getId()).orElseThrow();
        assertEquals(null, saved.getEmployeeCode());
        assertEquals(null, saved.getUserCode());
        assertEquals(null, saved.getDepartment());
        // and a second employee can be cleared the same way without a clash
        assertEquals(200, update(body(b.getId(), "", "", "Trần Thị B", null, "true")).getResponse().getStatus());
    }

    @Test
    void anotherEmployeesCodeIsA409ButKeepingYourOwnIsFine() throws Exception {
        assertEquals(409, update(body(a.getId(), "TNS0002", "1", "x", null, "true")).getResponse().getStatus());
        assertEquals(409, update(body(a.getId(), "TNS0001", "2", "x", null, "true")).getResponse().getStatus());
        assertEquals(200, update(body(a.getId(), "TNS0001", "1", "Chỉ đổi tên", null, "true")).getResponse()
                .getStatus());
    }

    @Test
    void badRequestsAreRejected() throws Exception {
        assertEquals(404, update(body(999999, "X", "9", "x", null, "true")).getResponse().getStatus());
        assertEquals(404, update(body(a.getId(), "TNS0001", "1", "x", 999999, "true")).getResponse().getStatus());
        // active left out would deactivate by accident: required
        assertEquals(400, update(body(a.getId(), "TNS0001", "1", "x", null, null)).getResponse().getStatus());
        assertEquals(400, update(body(a.getId(), "A".repeat(21), "1", "x", null, "true")).getResponse().getStatus());
        // nothing changed by the rejected calls
        assertEquals("TNS0001", employeeRepository.findById(a.getId()).orElseThrow().getEmployeeCode());
    }

    // ---- delete

    @Test
    void deletesTheEmployeeButKeepsTheirPunches() throws Exception {
        PunchRaw punch = new PunchRaw();
        punch.setBranchCode("PKD_ND");
        punch.setUserCode("1");
        punch.setPunchTime(LocalDateTime.parse("2026-10-14T07:30:00"));
        punchRawRepository.save(punch);

        mockMvc.perform(delete(URL + "/" + a.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(employeeRepository.findById(a.getId()).isEmpty());
        assertTrue(employeeRepository.findById(b.getId()).isPresent());
        assertEquals(1, punchRawRepository.count());
        mockMvc.perform(delete(URL + "/" + a.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void needLoginAndAreRegisteredAsPermissions() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/1")).andExpect(status().isUnauthorized());
        assertTrue(permissionRepository.findByApiPathAndMethod(URL, "PUT").isPresent());
        assertTrue(permissionRepository.findByApiPathAndMethod(URL + "/{id}", "DELETE").isPresent());
    }
}
