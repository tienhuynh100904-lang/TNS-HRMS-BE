package vn.com.tns_hrms.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * identified by (branch_code, user_code, punch_time), not by the device record id, so pulling the same period
 * again (or from a device that renumbered its records) cannot create duplicates.
 * The punch is linked to the branch it was made at (branch), whatever branch the employee belongs to.
 */
@Entity
@Table(name = "punch_raw", uniqueConstraints = @UniqueConstraint(name = "uq_punch", columnNames = { "branch_code",
        "user_code", "punch_time" }), indexes = @Index(name = "ix_punch_branch_time", columnList = "branch_code, punch_time"))
@NoArgsConstructor
@Getter
@Setter
public class PunchRaw {
    public enum Direction {
        IN,
        OUT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_code", length = 20, nullable = false)
    private String branchCode;

    // read-only association: the branch_code column is owned by the field above (same length as branches.branch_code)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_code", insertable = false, updatable = false)
    private Branch branch;

    // the person as the device reports them (face / fingerprint machines: user_id / uid); part of the key that
    // identifies a punch. It is NOT the employee code: employees.user_code maps it to the employee.
    @Column(name = "user_code", length = 20, nullable = false)
    private String userCode;

    // employee code, only when the device itself reports it (WiseEye: StaffID); null for face / fingerprint machines
    @Column(name = "employee_code", length = 20)
    private String employeeCode;

    // number of the card used, only when the device reads cards (WiseEye: CardID); null for face or fingerprint
    @Column(name = "employee_card_id", length = 20)
    private String employeeCardId;

    // date + time of the punch (device local time)
    @Column(nullable = false)
    private LocalDateTime punchTime;

    //id trên thiết bị, có thể trùng nhau nếu thiết bị reset lại id
    @Column(length = 50)
    private String sourceRef;

    // Hình thức xác thực
    private Integer verifyType;

    // in or out as the device reports it (WiseEye: TA 1 = IN, 2 = OUT, 0 = unknown); null when the device does not say
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Direction direction;
}
