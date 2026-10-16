package vn.com.tns_hrms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One attendance session of an employee, computed from punch_raw: the first punch of the session is the
 * check-in and the last one is the check-out; punches in between are only counted. A session is not tied to
 * the calendar day, so a night shift (e.g. 18:00 - 06:00) stays in a single row. Because of that the same employee
 * may have more than one session on the same work_date, so a session is identified by its check_in_time, not by
 * its work_date.
 * The table is derived data: to recompute an employee, delete its rows in the affected period and insert the new ones
 * (never update a row to another check_in_time), then punch_raw stays the only source of truth.
 * All punches of a session are the punch_raw rows of the same (branch_code, user_code) with punch_time between
 * check_in_time and check_out_time.
 */
@Entity
@Table(name = "attendance", uniqueConstraints = @UniqueConstraint(name = "uq_attendance", columnNames = {
        "branch_code", "user_code", "check_in_time" }), indexes = @Index(name = "ix_attendance_work_date", columnList = "branch_code, user_code, work_date"))
@NoArgsConstructor
@Getter
@Setter
public class Attendance {
    public enum Status {
        OK,
        SINGLE_PUNCH,
        IN_PROGRESS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_code", length = 20, nullable = false)
    private String branchCode;

    // date of the first punch of the session (the day the shift starts)
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    // user code of the punches of the session (punch_raw.user_code)
    @Column(name = "user_code", length = 20, nullable = false)
    private String userCode;

    // soft, read-only link to the employee that owns this user code: null when no employee has it (yet)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_code", referencedColumnName = "user_code", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private Employee employee;

    @Column(nullable = false)
    private LocalDateTime checkInTime;

    // null when the session has a single punch
    private LocalDateTime checkOutTime;

    // the punch_raw rows picked as check-in / check-out (no FK, punch_raw stays untouched)
    @Column(nullable = false)
    private Long checkInPunchId;

    private Long checkOutPunchId;

    // all punches of the session, including the ones between check-in and check-out
    @Column(nullable = false)
    private int punchCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Status status;

    // reserved for when shift information is available
    @Column(length = 20)
    private String shiftCode;

    @Column(nullable = false)
    private int ruleVersion;

    @Column(nullable = false)
    private LocalDateTime computedAt;
}
