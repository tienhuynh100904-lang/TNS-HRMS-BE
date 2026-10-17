package vn.com.tns_hrms.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.tns_hrms.entity.Attendance;

/** The attendance of one person on one day, worked out from that day's punches. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResDailyAttendance {
    private String branchCode;
    private String userCode;
    // the employee that owns the user code; null when no employee has it yet
    private String employeeCode;
    private String fullName;
    private String departmentName;
    private LocalDate workDate;
    // first punch of the day
    private LocalDateTime checkInTime;
    // last punch of the day; null when there is only one punch (or all punches are within a minute or two)
    private LocalDateTime checkOutTime;
    private long punchCount;
    // OK: check-in and check-out; SINGLE_PUNCH: cannot tell an in from an out; IN_PROGRESS: single punch today,
    // the check-out may still come
    private Attendance.Status status;
}
