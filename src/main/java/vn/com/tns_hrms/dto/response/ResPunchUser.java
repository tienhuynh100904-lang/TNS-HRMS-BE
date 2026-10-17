package vn.com.tns_hrms.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One punch: when it was made, and by whom. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResPunchUser {
    private String branchCode;
    // the code of the person on the device
    private String userCode;
    // the employee that owns the user code; null when no employee has it yet
    private String employeeCode;
    private String fullName;
    private String departmentName;
    private LocalDateTime punchTime;
}
