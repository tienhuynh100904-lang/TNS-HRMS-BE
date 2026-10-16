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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person working for the company. An employee is not tied to a branch: the branch of a punch is the branch of
 * the device it was made on (punch_raw.branch_code), so the same person can punch at any branch.
 */
@Entity
@Table(name = "employees")
@NoArgsConstructor
@Getter
@Setter
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // employee code given by HR (WiseEye: StaffID), unique when set
    @Column(name = "employee_code", length = 20, unique = true)
    private String employeeCode;

    // code of the person on the attendance devices (face and fingerprint machines report it as user_id / uid).
    // One per person and unique across all devices, so it is the link from punch_raw.user_code to the employee.
    @Column(name = "user_code", length = 20, unique = true)
    private String userCode;

    // current attendance card code, informational only (cards are reissued, punches are matched by user_code)
    @Column(name = "card_code", length = 20)
    private String cardCode;

    // null while the device only has a placeholder name (e.g. "NN-1570")
    @Nationalized
    @Column(name = "full_name", length = 255)
    private String fullName;

    // optional: an employee may have no department yet (department_id is nullable)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "department_id", nullable = true)
    private Department department;

    
    private boolean active = true;
}
