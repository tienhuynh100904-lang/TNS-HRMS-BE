package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.repository.EmployeeRepository;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeUserCodeTest {
    @Autowired
    EmployeeRepository employeeRepository;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
    }

    private Employee employee(String employeeCode, String userCode) {
        Employee e = new Employee();
        e.setEmployeeCode(employeeCode);
        e.setUserCode(userCode);
        return e;
    }

    @Test
    void theUserCodeOfTheDeviceFindsTheEmployee() {
        employeeRepository.save(employee("TNS0351", "5428"));
        employeeRepository.save(employee("TNS0352", "5429"));

        assertEquals("TNS0351", employeeRepository.findByUserCode("5428").orElseThrow().getEmployeeCode());
        assertTrue(employeeRepository.findByUserCode("9999").isEmpty());
    }

    @Test
    void employeesWithoutAUserCodeAreFineButAUserCodeBelongsToOnePersonOnly() {
        // people who never use a device have no user code, any number of them
        employeeRepository.save(employee("A1", null));
        employeeRepository.save(employee("A2", null));
        assertEquals(2, employeeRepository.count());

        employeeRepository.save(employee("B1", "77"));
        assertThrows(DataIntegrityViolationException.class, () -> employeeRepository.saveAndFlush(employee("B2", "77")));
    }
}
