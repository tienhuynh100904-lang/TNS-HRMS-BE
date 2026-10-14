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
public class ResRoleStats {
    private long totalRoles;
    private long activeRoles;
    private long totalPermissions;
    private long totalModules;
}
