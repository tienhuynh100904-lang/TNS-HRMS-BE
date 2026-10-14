package vn.com.tns_hrms.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class AttendanceDailyId implements Serializable {
    @Column(name = "branch_code")
    private String branchCode;

    @Column(name = "staff_code")
    private String staffCode;

    @Column(name = "work_date")
    private LocalDate workDate;
}
