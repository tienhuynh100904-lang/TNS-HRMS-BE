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
 * An attendance machine installed at a branch. A branch can have several machines, and every punch records the
 * machine it came from, so a punch tells both the branch and the machine.
 */
@Entity
@Table(name = "devices", uniqueConstraints = @UniqueConstraint(name = "uq_device_name", columnNames = {
        "branch_code", "name" }))
@NoArgsConstructor
@Getter
@Setter
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_code", length = 20, nullable = false)
    private String branchCode;

    // read-only association: the branch_code column is owned by the field above (same length as branches.branch_code)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_code", insertable = false, updatable = false)
    private Branch branch;

    // e.g. "Cổng chính", "Xưởng 2"
    @Nationalized
    @Column(length = 100, nullable = false)
    private String name;
}
