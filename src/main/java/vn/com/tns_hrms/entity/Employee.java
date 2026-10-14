package vn.com.tns_hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employees")
@NoArgsConstructor
@Getter
@Setter
public class Employee {
    @EmbeddedId
    private EmployeeId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("branchCode")
    @JoinColumn(name = "branch_code")
    private Branch branch;

    @Column(length = 255)
    private String fullName;

    @Column(length = 100)
    private String department;
}
