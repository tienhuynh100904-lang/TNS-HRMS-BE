package vn.com.tns_hrms.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.response.ResDailyAttendance;
import vn.com.tns_hrms.dto.response.ResEmployeePunch;
import vn.com.tns_hrms.dto.response.ResPunchUser;
import vn.com.tns_hrms.dto.response.ResultPagination;
import vn.com.tns_hrms.entity.Attendance;
import vn.com.tns_hrms.entity.Employee;
import vn.com.tns_hrms.entity.PunchRaw;
import vn.com.tns_hrms.exception.BadRequestException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.mapper.PaginationMapper;
import vn.com.tns_hrms.repository.EmployeeRepository;
import vn.com.tns_hrms.repository.PunchRawRepository;
import vn.com.tns_hrms.repository.PunchReportRepository;
import vn.com.tns_hrms.repository.PunchReportRepository.PunchLine;
import vn.com.tns_hrms.repository.PunchReportRepository.PunchSummary;

/** Reports on the punches in punch_raw: the punches of a day, and the attendance of each person on a day. */
@Service
public class AttendanceReportService {
    // a last punch this close to the first one is the same swipe seen twice, not a check-out
    static final Duration MIN_SESSION = Duration.ofMinutes(2);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // "no limit", inside what SQL Server's datetime2 can hold
    private static final LocalDateTime EARLIEST = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(9999, 1, 1, 0, 0);

    private final PunchReportRepository punchReportRepository;
    private final PunchRawRepository punchRawRepository;
    private final EmployeeRepository employeeRepository;

    public AttendanceReportService(PunchReportRepository punchReportRepository,
            PunchRawRepository punchRawRepository, EmployeeRepository employeeRepository) {
        this.punchReportRepository = punchReportRepository;
        this.punchRawRepository = punchRawRepository;
        this.employeeRepository = employeeRepository;
    }

    /** Every punch of one calendar day, oldest first: the time of the punch and who made it. */
    @Transactional(readOnly = true)
    public ResultPagination getPunchUsers(LocalDate date, String branchCode, String keyword, Pageable pageable) {
        Page<PunchLine> page = punchReportRepository.punches(clean(branchCode), clean(keyword), date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), pageable);
        List<ResPunchUser> punches = page.getContent().stream()
                .map(l -> new ResPunchUser(l.branchCode(), l.userCode(), l.employeeCode(), l.fullName(),
                        l.departmentName(), l.punchTime()))
                .toList();
        return PaginationMapper.toPaginationDTO(page, punches);
    }

    /**
     * The attendance of every person that punched on the given calendar day: the first punch is the check-in and
     * the last one the check-out. A night shift that crosses midnight is split between the two days.
     */
    @Transactional(readOnly = true)
    public ResultPagination getDailyAttendance(LocalDate date, String branchCode, String keyword, Pageable pageable) {
        Page<PunchSummary> page = punchReportRepository.summarize(clean(branchCode), clean(keyword),
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
        boolean today = date.equals(LocalDate.now(ZONE));
        List<ResDailyAttendance> days = page.getContent().stream().map(s -> toDaily(s, date, today)).toList();
        return PaginationMapper.toPaginationDTO(page, days);
    }

    /**
     * The punches of one employee, filtered by time (whole days, both included, either may be null) and sorted by
     * time: oldest first, or newest first with sort=punchTime,desc. The punches are those of the employee's user code;
     * an employee without a user code (no device) simply has none.
     */
    @Transactional(readOnly = true)
    public ResultPagination getMonthlyAttendance(long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        if (pageable.getSort().stream().anyMatch(o -> !o.getProperty().equals("punchTime"))) {
            throw new BadRequestException("Punches can only be sorted by punchTime");
        }
        Sort.Order order = pageable.getSort().getOrderFor("punchTime");
        Sort.Direction direction = order == null ? Sort.Direction.ASC : order.getDirection();
        // id as the second key keeps the order stable when two punches share the same second
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(direction, "punchTime").and(Sort.by(direction, "id")));

        Page<PunchRaw> punches = employee.getUserCode() == null ? Page.empty(page)
                : punchRawRepository.findPunches(employee.getUserCode(),
                        from == null ? EARLIEST : from.atStartOfDay(),
                        to == null ? LATEST : to.plusDays(1).atStartOfDay(), page);
        return PaginationMapper.toPaginationDTO(punches, punches.getContent().stream()
                .map(p -> new ResEmployeePunch(p.getPunchTime(), p.getBranchCode(), p.getDirection())).toList());
    }

    private static ResDailyAttendance toDaily(PunchSummary s, LocalDate date, boolean today) {
        boolean hasCheckOut = hasCheckOut(s.punchCount(), s.firstPunchTime(), s.lastPunchTime());
        return new ResDailyAttendance(s.branchCode(), s.userCode(), s.employeeCode(), s.fullName(),
                s.departmentName(), date, s.firstPunchTime(), hasCheckOut ? s.lastPunchTime() : null,
                s.punchCount(), statusOf(hasCheckOut, today));
    }

    // the rule of a day shared by every report: the last punch is a check-out only when it is a real second swipe
    private static boolean hasCheckOut(long punchCount, LocalDateTime first, LocalDateTime last) {
        return punchCount >= 2 && Duration.between(first, last).compareTo(MIN_SESSION) >= 0;
    }

    private static Attendance.Status statusOf(boolean hasCheckOut, boolean today) {
        return hasCheckOut ? Attendance.Status.OK : today ? Attendance.Status.IN_PROGRESS : Attendance.Status.SINGLE_PUNCH;
    }

    // an empty filter means no filter
    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
