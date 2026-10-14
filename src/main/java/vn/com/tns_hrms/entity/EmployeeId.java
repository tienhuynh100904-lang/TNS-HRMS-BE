package vn.com.tns_hrms.entity;

import java.io.Serializable;

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
public class EmployeeId implements Serializable {
    @Column(length = 20, nullable = false)
    private String branchCode;

    // original employee code from the attendance system
    @Column(length = 20, nullable = false)
    private String staffCode;
}
