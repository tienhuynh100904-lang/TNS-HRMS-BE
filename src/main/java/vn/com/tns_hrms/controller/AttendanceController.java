package vn.com.tns_hrms.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.response.ResPullResult;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.service.PunchPullService;
import vn.com.tns_hrms.service.AttendanceReportService;

@RestController
@RequestMapping("/api/v1/attendance")
@Validated
public class AttendanceController {
    private final AttendanceReportService attendanceReportService;
    private final PunchPullService punchPullService;

    public AttendanceController(AttendanceReportService attendanceReportService,
            PunchPullService punchPullService) {
        this.attendanceReportService = attendanceReportService;
        this.punchPullService = punchPullService;
    }

    /**
     * Pulls the punches from the attendance device(s) right now instead of waiting for the nightly job, and
     * answers when it is done (it can take a while on the first pull). branchCode (optional) pulls only that
     * branch's device, otherwise every configured device. The answer is 200 with one entry per device: its
     * counts, or the error when that device failed. 404 when no device matches, 409 when a pull is already running.
     */
    @PostMapping("/pull")
    public ResponseEntity<ApiResponse<List<ResPullResult>>> pullNow(
            @RequestParam(required = false) String branchCode) {
        ApiResponse<List<ResPullResult>> response = ApiResponse.<List<ResPullResult>>builder()
                .status(HttpStatus.OK.value())
                .message("Pull finished")
                .data(punchPullService.pullNow(branchCode == null || branchCode.isBlank() ? null : branchCode.trim()))
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * The punches of one day, paged (page starts at 0, default size 20), oldest first; one row per punch with
     * its time and who made it. date is required (yyyy-MM-dd); branchCode and keyword (user code, employee code or
     * name) are optional filters.
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<ResultPagination>> getPunchUsers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Punches retrieved successfully")
                .data(attendanceReportService.getPunchUsers(date, branchCode, keyword, pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * All the punches of one employee, paged (page starts at 0, default size 20). employeeId is required; from / to
     * (yyyy-MM-dd, both days included) limit the period; sort=punchTime,asc (default) or punchTime,desc orders them
     * by time. Each entry has the time and the branch of the punch. 404 when the employee does not exist.
     */
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<ResultPagination>> getMonthlyAttendance(
            @RequestParam long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Employee punches retrieved successfully")
                .data(attendanceReportService.getMonthlyAttendance(employeeId, from, to, pageable))
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Attendance of every person on one day, paged. date is required (yyyy-MM-dd); branchCode and keyword
     * are optional filters.
     */
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<ResultPagination>> getDailyAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        ApiResponse<ResultPagination> response = ApiResponse.<ResultPagination>builder()
                .status(HttpStatus.OK.value())
                .message("Daily attendance retrieved successfully")
                .data(attendanceReportService.getDailyAttendance(date, branchCode, keyword, pageable))
                .build();
        return ResponseEntity.ok(response);
    }
}
