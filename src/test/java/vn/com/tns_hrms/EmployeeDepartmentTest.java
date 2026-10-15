package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import vn.com.tns_hrms.entity.Department;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.repository.DepartmentRepository;
import vn.com.tns_hrms.repository.EmployeeRepository;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeDepartmentTest {
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    DepartmentRepository departmentRepository;

    @Test
    void anEmployeeNeedsNoDepartmentAndADepartmentNeedsNoEmployee() {
        Employee noDepartment = new Employee();
        noDepartment.setEmployeeCode("TST0001");
        noDepartment.setFullName("Nhân viên chưa có phòng ban");
        employeeRepository.save(noDepartment);

        Department empty = new Department();
        empty.setCode("TST_EMPTY");
        empty.setName("Phòng ban chưa có nhân viên");
        departmentRepository.save(empty);

        Employee saved = employeeRepository.findAll().stream()
                .filter(e -> "TST0001".equals(e.getEmployeeCode())).findFirst().orElseThrow();
        assertNull(saved.getDepartment());
        assertEquals(1, departmentRepository.findAll().stream().filter(d -> "TST_EMPTY".equals(d.getCode())).count());

        // an employee can be given a department later, and then be moved out of it again
        saved.setDepartment(departmentRepository.findAll().stream().filter(d -> "TST_EMPTY".equals(d.getCode()))
                .findFirst().orElseThrow());
        employeeRepository.save(saved);
        saved.setDepartment(null);
        employeeRepository.save(saved);
        assertNull(employeeRepository.findById(saved.getId()).orElseThrow().getDepartment());
    }
}
