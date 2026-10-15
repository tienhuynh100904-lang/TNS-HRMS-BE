package vn.com.tns_hrms.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * One raw punch pulled from a device. Insert-only: a row is never updated after it is written, and a punch is
 * identified by (branch_code, employee_card_id, punch_time), not by the device record id, so pulling the same period
 * again (or from a device that renumbered its records) cannot create duplicates. See PunchImportService.
 * The punch is linked to the machine it came from (device) and to the branch of that machine (branch).
 */
@Entity
@Table(name = "punch_raw", uniqueConstraints = @UniqueConstraint(name = "uq_punch", columnNames = { "branch_code",
        "employee_card_id", "punch_time" }), indexes = @Index(name = "ix_punch_branch_time", columnList = "branch_code, punch_time"))
@NoArgsConstructor
@Getter
@Setter
public class PunchRaw {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_code", length = 20, nullable = false)
    private String branchCode;

    // read-only association: the branch_code column is owned by the field above (same length as branches.branch_code)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_code", insertable = false, updatable = false)
    private Branch branch;

    // the machine the punch was pulled from; its branch is always the branch_code of this row
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // company wide employee code; the device does not report it, so it stays null until the card is linked
    @Column(length = 20)
    private String employeeCode;

    // card code as the device reports it (user_id); part of the key that identifies a punch
    @Column(length = 20, nullable = false)
    private String employeeCardId;

    // date + time of the punch (device local time)
    @Column(nullable = false)
    private LocalDateTime punchTime;

    //id trên thiết bị, có thể trùng nhau nếu thiết bị reset lại id
    @Column(length = 50)
    private String sourceRef;

    // Hình thức xác thực
    private Integer verifyType;
}
