package vn.com.tns_hrms.dto.response.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResAccountStats {
    private long totalAccounts;
    private long activeAccounts;
    private long adminAccounts;
}
