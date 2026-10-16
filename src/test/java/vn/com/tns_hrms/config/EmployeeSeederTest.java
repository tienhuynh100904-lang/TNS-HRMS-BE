package vn.com.tns_hrms.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.config.seed.DepartmentSeeder;
import vn.com.tns_hrms.config.seed.EmployeeSeeder;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.repository.DepartmentRepository;
import vn.com.tns_hrms.repository.EmployeeRepository;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeSeederTest {
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    DepartmentSeeder departmentSeeder;
    @Autowired
    EmployeeSeeder employeeSeeder;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshDepartmentsAndNoEmployees() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        // department_id of employees.csv are the ids DepartmentSeeder gives on an empty table: 1, 2, 3...
        jdbc.execute("ALTER TABLE departments ALTER COLUMN id RESTART WITH 1");
        departmentSeeder.seed();
    }

    @Test
    @Transactional
    void createsTheEmployeesOfTheCsvWithTheirUserCodeAndDepartment() {
        employeeSeeder.seed();

        assertEquals(88, employeeRepository.count());
        Employee first = employeeRepository.findByUserCode("1").orElseThrow();
        assertEquals("TNS1224", first.getEmployeeCode());
        assertEquals("PHAN VĂN THANH", first.getFullName());
        assertEquals("ERP", first.getDepartment().getCode());
        assertTrue(first.isActive());

        // a row without employee code and without department is still created
        Employee noCode = employeeRepository.findByUserCode("4").orElseThrow();
        assertNull(noCode.getEmployeeCode());
        assertNull(noCode.getDepartment());
        assertEquals("PHAM HUU THAI", noCode.getFullName());
    }

    @Test
    void runningAgainNeverDuplicatesOrOverwrites() {
        employeeSeeder.seed();
        Employee edited = employeeRepository.findByUserCode("1").orElseThrow();
        edited.setFullName("Tên do người dùng sửa");
        edited.setActive(false);
        employeeRepository.save(edited);

        employeeSeeder.seed();

        assertEquals(88, employeeRepository.count());
        Employee kept = employeeRepository.findByUserCode("1").orElseThrow();
        assertEquals("Tên do người dùng sửa", kept.getFullName());
        assertEquals(false, kept.isActive());
    }

    @Test
    void anEmployeeThatAlreadyExistsIsSkippedAndTheRestIsAdded() {
        Employee manual = new Employee();
        manual.setEmployeeCode("TNS1224");
        manual.setFullName("Nhập tay");
        employeeRepository.save(manual);

        employeeSeeder.seed();

        // 88 rows in the file, the one with TNS1224 already existed (found by employee code)
        assertEquals(88, employeeRepository.count());
        assertEquals("Nhập tay", employeeRepository.findAll().stream()
                .filter(e -> "TNS1224".equals(e.getEmployeeCode())).findFirst().orElseThrow().getFullName());
    }

    @Test
    @Transactional
    void anUnknownDepartmentIdLeavesTheEmployeeWithoutDepartmentButStillCreated() {
        // department 13 (HC) is gone: its employees must still be created
        departmentRepository.deleteAll(departmentRepository.findAll().stream()
                .filter(d -> d.getCode().equals("HC")).toList());

        employeeSeeder.seed();

        assertEquals(88, employeeRepository.count());
        assertTrue(employeeRepository.findAll().stream().allMatch(e -> e.getDepartment() == null
                || !e.getDepartment().getCode().equals("HC")));
    }
}
