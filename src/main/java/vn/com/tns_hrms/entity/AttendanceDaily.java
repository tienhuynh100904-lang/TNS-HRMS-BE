package vn.com.tns_hrms.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only daily summary of punches (the AttendanceDaily view): first punch of the day is
 * the check-in, the last one is the check-out (only when there is more than one punch).
 * Mapped with @Subselect so no table or view needs to be created by ddl-auto.
 */
@Entity
@Immutable
@Subselect("""
        SELECT p.branch_code AS branch_code,
               p.staff_code AS staff_code,
               CAST(p.punch_time AS DATE) AS work_date,
               e.full_name AS full_name,
               e.department AS department,
               MIN(p.punch_time) AS first_in,
               CASE WHEN COUNT(*) > 1 THEN MAX(p.punch_time) END AS last_out
        FROM punch_raw p
        LEFT JOIN employees e ON e.branch_code = p.branch_code AND e.staff_code = p.staff_code
        GROUP BY p.branch_code, p.staff_code, e.full_name, e.department, CAST(p.punch_time AS DATE)
        """)
@Synchronize({ "punch_raw", "employees" })
@NoArgsConstructor
@Getter
public class AttendanceDaily {
    @EmbeddedId
    private AttendanceDailyId id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "department")
    private String department;

    @Column(name = "first_in")
    private LocalDateTime firstIn;

    @Column(name = "last_out")
    private LocalDateTime lastOut;
}
