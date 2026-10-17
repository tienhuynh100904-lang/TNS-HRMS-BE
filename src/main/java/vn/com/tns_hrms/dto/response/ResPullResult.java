package vn.com.tns_hrms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How the pull of one device went: either its import counts, or why it failed. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResPullResult {
    private String branchCode;
    // set when the pull worked; null when it failed
    private ResImportResult result;
    // set when the pull failed; null when it worked
    private String error;
}
