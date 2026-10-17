package vn.com.tns_hrms.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.tns_hrms.entity.PunchRaw;

/** One punch of an employee: when, and at which branch. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResEmployeePunch {
    private LocalDateTime punchTime;
    // the branch where the punch was made: the employee is not tied to a branch
    private String branchCode;
    // null when the device does not say whether it is an in or an out
    private PunchRaw.Direction direction;
}
