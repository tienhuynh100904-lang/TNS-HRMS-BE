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
 * Creates the employees listed in classpath:seed/employees.csv
 * (employee_code, user_code, card_code, full_name, department_id, active).
 * <ul>
 * <li>Only missing employees are inserted. A row is recognised by its employee_code or its user_code (both are
 * unique): when either already belongs to an employee the row is skipped, so nothing is overwritten or duplicated
 * when the application restarts, and edits made in the database are kept.</li>
 * <li>A row with neither code cannot be recognised again later, so it is skipped with a warning.</li>
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
        Map<Long, Department> departments = new HashMap<>();
        for (Department department : departmentRepository.findAll()) {
            departments.put(department.getId(), department);
        }

        Set<String> employeeCodes = new HashSet<>();
        Set<String> userCodes = new HashSet<>();
        int created = 0;
        int existing = 0;
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
            if ((employeeCode != null && employeeRepository.existsByEmployeeCode(employeeCode))
                    || (userCode != null && employeeRepository.existsByUserCode(userCode))) {
                existing++;
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
        log.info("Created {} employee(s), {} already existed, {} skipped", created, existing, skipped);
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
