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
public class ResPermissionStats {
    private long totalPermissions;
    private long activePermissions;
    private long totalModules;
}
