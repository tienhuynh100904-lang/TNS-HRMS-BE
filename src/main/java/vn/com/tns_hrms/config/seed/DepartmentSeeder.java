package vn.com.tns_hrms.config.seed;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.entity.Department;
import vn.com.tns_hrms.repository.DepartmentRepository;

/**
 * Creates the first departments from classpath:seed/departments.csv (id, code, name), only while the departments
 * table is empty. Once there is any department the file is no longer read, so departments that were renamed, added
 * or deleted afterwards are never touched or brought back when the application restarts.
 * The id column of the file is not used: the database assigns the ids, in file order (1, 2, 3... on an empty table).
 */
@Component
public class DepartmentSeeder {
    public static final String DEPARTMENTS_CSV = "seed/departments.csv";

    private static final Logger log = LoggerFactory.getLogger(DepartmentSeeder.class);

    private final DepartmentRepository departmentRepository;

    public DepartmentSeeder(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public void seed() {
        log.info(">>> Init Departments");
        if (departmentRepository.count() > 0) {
            log.info("Departments already exist, nothing to seed");
            return;
        }

        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        int created = 0;
        for (String[] row : SeedCsv.read(DEPARTMENTS_CSV, 3)) {
            String code = row[1];
            String name = row[2];
            // code and name are both unique and required
            if (code.isEmpty() || name.isEmpty() || !codes.add(code) || !names.add(name)) {
                log.warn("Skipping department with empty or repeated code/name in {}: {} / {}", DEPARTMENTS_CSV, code,
                        name);
                continue;
            }
            Department department = new Department();
            department.setCode(code);
            department.setName(name);
            departmentRepository.save(department);
            created++;
        }
        log.info("Created {} department(s)", created);
    }
}
