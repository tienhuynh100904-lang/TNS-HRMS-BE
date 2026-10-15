package vn.com.tns_hrms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Outcome of one import. received = inserted + updated + duplicates/unchanged + skipped. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResImportResult {
    private int received;
    // new rows written
    private int inserted;
    // existing rows that only had an empty value filled in (employees only, punches are never updated)
    private int updated;
    // rows already stored (or repeated inside the same batch): left untouched
    private int duplicates;
    // rows that cannot be stored (blank code, missing time, ...) or are not meant to be stored (device admins)
    private int skipped;
}
