package vn.com.tns_hrms.config.seed;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.entity.Department;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.repository.DepartmentRepository;
import vn.com.tns_hrms.repository.EmployeeRepository;

/**
 * Creates the first employees from classpath:seed/employees.csv
 * (employee_code, user_code, card_code, full_name, department_id, active), only while the employees table is empty.
 * Once there is any employee the file is no longer read, so employees that were edited, added or deleted afterwards
 * are never touched or brought back when the application restarts.
 * <ul>
 * <li>A row with neither employee_code nor user_code is skipped with a warning, and so is a row that repeats a code
 * already used earlier in the file (both codes are unique).</li>
 * <li>department_id is the id of a department as created by DepartmentSeeder; an empty or unknown id leaves the
 * employee without a department (the employee is still created).</li>
 * </ul>
 */
@Component
public class EmployeeSeeder {
    public static final String EMPLOYEES_CSV = "seed/employees.csv";

    private static final Logger log = LoggerFactory.getLogger(EmployeeSeeder.class);

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public EmployeeSeeder(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public void seed() {
        log.info(">>> Init Employees");
        if (employeeRepository.count() > 0) {
            log.info("Employees already exist, nothing to seed");
            return;
        }
        Map<Long, Department> departments = new HashMap<>();
        for (Department department : departmentRepository.findAll()) {
            departments.put(department.getId(), department);
        }

        Set<String> employeeCodes = new HashSet<>();
        Set<String> userCodes = new HashSet<>();
        int created = 0;
        int skipped = 0;
        for (String[] row : SeedCsv.read(EMPLOYEES_CSV, 6)) {
            String employeeCode = blankToNull(row[0]);
            String userCode = blankToNull(row[1]);
            if (employeeCode == null && userCode == null) {
                log.warn("Skipping employee without employee_code and user_code in {}: {}", EMPLOYEES_CSV, row[3]);
                skipped++;
                continue;
            }
            // the same code twice in the file would break the unique constraints
            if ((employeeCode != null && !employeeCodes.add(employeeCode))
                    || (userCode != null && !userCodes.add(userCode))) {
                log.warn("Skipping employee with a repeated code in {}: {} / {}", EMPLOYEES_CSV, employeeCode,
                        userCode);
                skipped++;
                continue;
            }

            Employee employee = new Employee();
            employee.setEmployeeCode(employeeCode);
            employee.setUserCode(userCode);
            employee.setCardCode(blankToNull(row[2]));
            employee.setFullName(blankToNull(row[3]));
            employee.setDepartment(department(row[4], departments, row[3]));
            employee.setActive(parseActive(row[5]));
            employeeRepository.save(employee);
            created++;
        }
        log.info("Created {} employee(s), {} skipped", created, skipped);
    }

    private static Department department(String rawId, Map<Long, Department> departments, String employeeName) {
        String id = rawId.trim();
        if (id.isEmpty()) {
            return null;
        }
        try {
            Department department = departments.get(Long.valueOf(id));
            if (department == null) {
                log.warn("Unknown department id {} for employee {}, created without a department", id, employeeName);
            }
            return department;
        } catch (NumberFormatException e) {
            log.warn("Unreadable department id '{}' for employee {}, created without a department", id,
                    employeeName);
            return null;
        }
    }

    // 1 / true / yes means active, 0 / false / no inactive; empty (or anything else) means active
    private static boolean parseActive(String value) {
        String v = value.trim().toLowerCase();
        return !(v.equals("0") || v.equals("false") || v.equals("no"));
    }

    private static String blankToNull(String value) {
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
