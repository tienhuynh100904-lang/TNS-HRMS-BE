package vn.com.tns_hrms.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import vn.com.tns_hrms.dto.request.ReqPunch;
import vn.com.tns_hrms.dto.response.ResImportResult;
import vn.com.tns_hrms.entity.PunchRaw;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.repository.BranchRepository;
import vn.com.tns_hrms.repository.PunchRawRepository;

/**
 * Writes punches pulled from a device into punch_raw without ever duplicating or overwriting a row.
 * <ol>
 * <li>A punch is identified by (branch_code, user_code, punch_time), not by the device record id, so it does
 * not matter how often, or in how many overlapping pulls, the same period is read.</li>
 * <li>Repeats inside the batch are dropped (the first one wins), then the ones already stored are dropped.
 * Only the remaining new punches are inserted; existing rows are never updated.</li>
 * <li>The unique constraint uq_punch is the final guard: if another import wins a race, the batch falls back to
 * row by row inserts and the rows that lost are counted as duplicates.</li>
 * </ol>
 * Importing the same data twice therefore inserts 0 rows the second time.
 */
@Service
public class PunchImportService {
    private static final Logger log = LoggerFactory.getLogger(PunchImportService.class);

    private static final int CHUNK_SIZE = 500;
    private static final int MAX_CODE_LENGTH = 20;

    private final PunchRawRepository punchRawRepository;
    private final BranchRepository branchRepository;

    public PunchImportService(PunchRawRepository punchRawRepository, BranchRepository branchRepository) {
        this.punchRawRepository = punchRawRepository;
        this.branchRepository = branchRepository;
    }

    /**
     * Not @Transactional on purpose: each repository call commits on its own, so one failing insert cannot roll
     * back the rows already written or poison the persistence context.
     */
    public ResImportResult importPunches(String branchCode, List<ReqPunch> punches) {
        if (!branchRepository.existsById(branchCode)) {
            throw new ResourceNotFoundException("Branch not found with code: " + branchCode);
        }

        int skipped = 0;
        int duplicates = 0;
        Set<String> seen = new HashSet<>();
        List<PunchRaw> candidates = new ArrayList<>();
        LocalDateTime from = null;
        LocalDateTime to = null;

        for (ReqPunch punch : punches) {
            String code = punch.getUserCode() == null ? "" : punch.getUserCode().trim();
            if (code.isEmpty() || code.length() > MAX_CODE_LENGTH || punch.getPunchTime() == null) {
                skipped++;
                continue;
            }
            if (!seen.add(key(code, punch.getPunchTime()))) {
                duplicates++;
                continue;
            }

            PunchRaw row = new PunchRaw();
            row.setBranchCode(branchCode);
            row.setUserCode(code);
            row.setPunchTime(punch.getPunchTime());
            row.setSourceRef(punch.getSourceRef());
            row.setVerifyType(punch.getVerifyType());
            row.setDirection(punch.getDirection());
            candidates.add(row);

            if (from == null || punch.getPunchTime().isBefore(from)) {
                from = punch.getPunchTime();
            }
            if (to == null || punch.getPunchTime().isAfter(to)) {
                to = punch.getPunchTime();
            }
        }

        List<PunchRaw> fresh = candidates;
        if (!candidates.isEmpty()) {
            Set<String> stored = new HashSet<>();
            for (PunchRawRepository.PunchKey stub : punchRawRepository.findKeys(branchCode, from, to)) {
                stored.add(key(stub.getUserCode(), stub.getPunchTime()));
            }
            fresh = candidates.stream()
                    .filter(row -> !stored.contains(key(row.getUserCode(), row.getPunchTime()))).toList();
            duplicates += candidates.size() - fresh.size();
        }

        int inserted = 0;
        for (int i = 0; i < fresh.size(); i += CHUNK_SIZE) {
            List<PunchRaw> chunk = fresh.subList(i, Math.min(i + CHUNK_SIZE, fresh.size()));
            try {
                punchRawRepository.saveAll(chunk);
                inserted += chunk.size();
            } catch (DataIntegrityViolationException e) {
                // someone else inserted part of this chunk after we looked: keep what is new, skip what is not
                log.warn("Concurrent punch import on branch {}, retrying one by one", branchCode);
                for (PunchRaw row : chunk) {
                    try {
                        punchRawRepository.save(row);
                        inserted++;
                    } catch (DataIntegrityViolationException duplicate) {
                        duplicates++;
                    }
                }
            }
        }

        log.info("Punch import {}: received={}, inserted={}, duplicates={}, skipped={}", branchCode, punches.size(),
                inserted, duplicates, skipped);
        return ResImportResult.builder().received(punches.size()).inserted(inserted).duplicates(duplicates)
                .skipped(skipped).build();
    }

    // time is part of the key at full precision, so two punches of the same person a second apart both count
    private static String key(String userCode, LocalDateTime punchTime) {
        return userCode + '|' + punchTime;
    }
}
