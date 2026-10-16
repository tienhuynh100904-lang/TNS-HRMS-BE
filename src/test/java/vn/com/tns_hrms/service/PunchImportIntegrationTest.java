package vn.com.tns_hrms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import vn.com.tns_hrms.dto.request.ReqPunch;
import vn.com.tns_hrms.dto.response.ResImportResult;
import vn.com.tns_hrms.entity.PunchRaw;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.helper.DeviceCsvHelper;
import vn.com.tns_hrms.repository.PunchRawRepository;

@SpringBootTest
@ActiveProfiles("test")
class PunchImportIntegrationTest {
    // seeded from seed/branches.csv
    private static final String BRANCH = "PKD_ND";
    // same layout as a file pulled from the device, with the awkward rows seen in real data:
    // a row without user_id, the same person twice in the same second, a time that could not be decoded
    private static final Path PUNCH_CSV = Path.of("src/test/resources/punches_sample.csv");

    @Autowired
    PunchImportService punchImportService;
    @Autowired
    DeviceCsvHelper csvHelper;
    @Autowired
    PunchRawRepository punchRawRepository;

    @BeforeEach
    void setUp() {
        punchRawRepository.deleteAll();
    }

    @Test
    void importingTheSamePunchFileTwiceInsertsNothingTheSecondTime() {
        List<ReqPunch> punches = csvHelper.readPunches(PUNCH_CSV);

        ResImportResult first = punchImportService.importPunches(BRANCH, punches);
        // 8 rows: 1 without user_id and 1 with an unreadable time are skipped, 1 repeats the same person and
        // second inside the file, the other 5 are new
        assertEquals(8, first.getReceived());
        assertEquals(2, first.getSkipped());
        assertEquals(1, first.getDuplicates());
        assertEquals(5, first.getInserted());
        assertEquals(5, punchRawRepository.count());

        // first line of the file: 1,2,2023-08-16 14:59:00,15,255,... -> user_id 2 is the user code (not an employee
        // code, that comes from the employee that owns the user code), and there is no card
        PunchRaw firstLine = punchRawRepository.findAll().stream().filter(p -> "1".equals(p.getSourceRef()))
                .findFirst().orElseThrow();
        assertEquals("2", firstLine.getUserCode());
        assertEquals(null, firstLine.getEmployeeCode());
        assertEquals(null, firstLine.getEmployeeCardId());
        assertEquals(LocalDateTime.of(2023, 8, 16, 14, 59, 0), firstLine.getPunchTime());
        assertEquals(15, firstLine.getVerifyType());
        assertEquals(BRANCH, firstLine.getBranchCode());

        ResImportResult second = punchImportService.importPunches(BRANCH, punches);
        assertEquals(0, second.getInserted());
        assertEquals(6, second.getDuplicates());
        assertEquals(2, second.getSkipped());
        assertEquals(5, punchRawRepository.count());
    }

    @Test
    void overlappingBatchOnlyAddsTheNewPunchesAndNeverOverwrites() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 5, 7, 30, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 5, 17, 5, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 1, 6, 7, 28, 0);

        punchImportService.importPunches(BRANCH, List.of(new ReqPunch("7", t1, "100", 15)));

        // same punch again with another device record id / verify / direction: must stay as first written
        ResImportResult result = punchImportService.importPunches(BRANCH,
                List.of(new ReqPunch("7", t1, "999", 1, PunchRaw.Direction.OUT), new ReqPunch("7", t2, "101", 15),
                        new ReqPunch("7", t3, "102", 15, PunchRaw.Direction.IN)));

        assertEquals(2, result.getInserted());
        assertEquals(1, result.getDuplicates());
        assertEquals(3, punchRawRepository.count());
        var original = punchRawRepository.findAll().stream().filter(p -> p.getPunchTime().equals(t1)).findFirst()
                .orElseThrow();
        assertEquals("100", original.getSourceRef());
        assertEquals(15, original.getVerifyType());
        assertEquals(null, original.getDirection());
        var added = punchRawRepository.findAll().stream().filter(p -> p.getPunchTime().equals(t3)).findFirst()
                .orElseThrow();
        assertEquals(PunchRaw.Direction.IN, added.getDirection());
        assertEquals(BRANCH, added.getBranchCode());
        assertEquals(t3, punchRawRepository.findLastPunchTime(BRANCH).orElseThrow());
    }

    @Test
    void samePersonCanPunchTwiceAndTheSameCodeCanExistInTwoBranches() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 5, 7, 30, 0);
        ResImportResult result = punchImportService.importPunches(BRANCH,
                List.of(new ReqPunch("7", t, "1", 15), new ReqPunch("7", t.plusSeconds(1), "2", 15)));
        assertEquals(2, result.getInserted());

        assertEquals(1, punchImportService.importPunches("PKD_XK", List.of(new ReqPunch("7", t, "1", 15)))
                .getInserted());
    }

    @Test
    void unknownBranchIsRejected() {
        assertThrows(ResourceNotFoundException.class, () -> punchImportService.importPunches("NOPE", List.of()));
    }
}
