package vn.com.tns_hrms.config.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.entity.Branch;
import vn.com.tns_hrms.repository.BranchRepository;

/**
 * Creates the branches listed in classpath:seed/branches.csv (branch_code, branch_name, source_system).
 * Only missing branches are inserted: a branch that already exists is left as it is, so a name or source system
 * edited in the database is never overwritten when the application restarts.
 */
@Component
public class BranchSeeder {
    public static final String BRANCHES_CSV = "seed/branches.csv";

    private static final Logger log = LoggerFactory.getLogger(BranchSeeder.class);

    private final BranchRepository branchRepository;

    public BranchSeeder(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Transactional
    public void seed() {
        log.info(">>> Init Branches");
        int created = 0;
        for (String[] row : SeedCsv.read(BRANCHES_CSV, 3)) {
            if (row[0].isEmpty()) {
                log.warn("Skipping a branch without code in {}", BRANCHES_CSV);
                continue;
            }
            if (!branchRepository.existsById(row[0])) {
                Branch branch = new Branch();
                branch.setBranchCode(row[0]);
                branch.setBranchName(row[1]);
                branch.setSourceSystem(row[2]);
                branchRepository.save(branch);
                created++;
            }
        }
        log.info("Created {} new branch(es)", created);
    }
}
