package vn.com.tns_hrms.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import vn.com.tns_hrms.config.seed.DepartmentSeeder;
import vn.com.tns_hrms.entity.Department;
import vn.com.tns_hrms.repository.DepartmentRepository;
import vn.com.tns_hrms.repository.EmployeeRepository;

@SpringBootTest
@ActiveProfiles("test")
class DepartmentSeederTest {
    private static final Path CSV = Path.of("src/main/resources/seed/departments.csv");

    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    DepartmentSeeder departmentSeeder;

    @BeforeEach
    void startFromAnEmptyTable() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    // [code, name] of every data row of the seed file, so the test follows the file when its data changes
    private static List<String[]> csvRows() throws IOException {
        return Files.readAllLines(CSV, StandardCharsets.UTF_8).stream().skip(1).filter(l -> !l.isBlank())
                .map(l -> l.split(",", 3)).map(c -> new String[] { c[1].trim(), c[2].trim() }).toList();
    }

    @Test
    void createsTheDepartmentsOfTheCsvInFileOrderWhenTheTableIsEmpty() throws IOException {
        List<String[]> expected = csvRows();

        departmentSeeder.seed();

        List<Department> departments = departmentRepository.findAll();
        assertEquals(expected.stream().map(r -> r[0]).toList(),
                departments.stream().map(Department::getCode).toList());
        assertEquals(expected.stream().map(r -> r[1]).toList(),
                departments.stream().map(Department::getName).toList());
        // names keep their Vietnamese characters
        assertEquals("KINH DOANH NỘI ĐỊA", departments.get(0).getName());
        // ids come from the database, in file order
        assertTrue(departments.get(0).getId() < departments.get(1).getId());
    }

    @Test
    void doesNothingOnceThereIsAnyDepartment() throws IOException {
        int total = csvRows().size();
        departmentSeeder.seed();
        Department second = departmentRepository.findAll().get(1);
        String secondCode = second.getCode();
        second.setName("Tên do người dùng sửa");
        departmentRepository.save(second);
        Department third = departmentRepository.findAll().get(2);
        String thirdCode = third.getCode();
        departmentRepository.delete(third);

        departmentSeeder.seed();

        // nothing brought back, nothing overwritten, nothing duplicated
        assertEquals(total - 1, departmentRepository.count());
        assertTrue(departmentRepository.findAll().stream().noneMatch(d -> d.getCode().equals(thirdCode)));
        assertEquals("Tên do người dùng sửa", departmentRepository.findAll().stream()
                .filter(d -> d.getCode().equals(secondCode)).findFirst().orElseThrow().getName());
    }
}
