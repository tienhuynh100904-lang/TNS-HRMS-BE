package vn.com.tns_hrms.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "punch_raw", uniqueConstraints = @UniqueConstraint(name = "uq_punch", columnNames = { "branch_code",
        "staff_code", "punch_time" }))
@NoArgsConstructor
@Getter
@Setter
public class PunchRaw {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String branchCode;

    @Column(length = 20, nullable = false)
    private String staffCode;

    // date + time of the punch (device local time)
    @Column(nullable = false)
    private LocalDateTime punchTime;

    @Column(length = 50)
    private String deviceName;

    // original RecID/ID from the device, used to prevent duplicates
    @Column(length = 50)
    private String sourceRef;
}
