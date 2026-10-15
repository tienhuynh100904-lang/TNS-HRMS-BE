package vn.com.tns_hrms.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import vn.com.tns_hrms.config.seed.BranchSeeder;
import vn.com.tns_hrms.entity.Branch;
import vn.com.tns_hrms.repository.BranchRepository;

@SpringBootTest
@ActiveProfiles("test")
class BranchSeederTest {
    @Autowired
    BranchRepository branchRepository;
    @Autowired
    BranchSeeder branchSeeder;

    @Test
    void branchesComeFromTheCsvOnStartup() {
        assertEquals(6, branchRepository.count());
        Branch branch = branchRepository.findById("PKD_ND").orElseThrow();
        assertEquals("Kinh doanh nội địa Nhà Bè", branch.getBranchName());
        assertEquals("WiseEyeMix3", branch.getSourceSystem());
        assertEquals("SmartTAS", branchRepository.findById("TNS_LS").orElseThrow().getSourceSystem());
    }

    @Test
    void seedingAgainNeverOverwritesOrDuplicates() {
        Branch edited = branchRepository.findById("KHO_BD").orElseThrow();
        edited.setBranchName("Tên do người dùng sửa");
        branchRepository.save(edited);

        branchSeeder.seed();

        assertEquals(6, branchRepository.count());
        assertEquals("Tên do người dùng sửa", branchRepository.findById("KHO_BD").orElseThrow().getBranchName());
    }
}
