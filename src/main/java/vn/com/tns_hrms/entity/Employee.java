package vn.com.tns_hrms.entity;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person working for the company. An employee has at most one attendance card, and the card code is only unique
 * inside a branch (the device that issued it), so the card is the pair (branch_code, card_code): both are set or
 * both are empty. An employee without a card simply does not punch on a device.
 */
@Entity
@Table(name = "employees", uniqueConstraints = @UniqueConstraint(name = "uq_employee_card", columnNames = {
        "branch_code", "card_code" }))
@NoArgsConstructor
@Getter
@Setter
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // company wide employee code, unique when set; empty until HR fills it in (the devices do not know it)
    @Column(length = 20, unique = true)
    private String employeeCode;

    // branch of the device the card belongs to; null when the employee has no card
    @Column(name = "branch_code", length = 20)
    private String branchCode;

    // read-only association: the branch_code column is owned by the field above (same length as branches.branch_code)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_code", insertable = false, updatable = false)
    private Branch branch;

    // attendance card code as the device reports it (user_id); null when the employee has no card
    @Column(name = "card_code", length = 20)
    private String cardCode;

    // null while the device only has a placeholder name (e.g. "NN-1570")
    @Nationalized
    @Column(length = 255)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // false for people who left: their card may stay on the device, but they are no longer expected to punch
    private boolean active = true;
}
