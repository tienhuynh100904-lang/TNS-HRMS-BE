package vn.com.tns_hrms.config.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
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
        for (Branch branch : readBranches()) {
            if (!branchRepository.existsById(branch.getBranchCode())) {
                branchRepository.save(branch);
                created++;
            }
        }
        log.info("Created {} new branch(es)", created);
    }

    private List<Branch> readBranches() {
        List<Branch> branches = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(BRANCHES_CSV).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header (may start with a BOM)
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length < 3 || unquote(cols[0]).isEmpty()) {
                    log.warn("Skipping unreadable line in {}: {}", BRANCHES_CSV, line);
                    continue;
                }
                Branch branch = new Branch();
                branch.setBranchCode(unquote(cols[0]));
                branch.setBranchName(unquote(cols[1]));
                branch.setSourceSystem(unquote(cols[2]));
                branches.add(branch);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + BRANCHES_CSV, e);
        }
        return branches;
    }

    private static String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }
}
