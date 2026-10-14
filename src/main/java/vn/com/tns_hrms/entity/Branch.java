package vn.com.tns_hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branches")
@NoArgsConstructor
@Getter
@Setter
public class Branch {
    // TNS_LS, VTS_LS, VTS_MY, PKD_XK, KHO_BD, PKD_ND
    @Id
    @Column(length = 20)
    private String branchCode;

    @Column(length = 100)
    private String branchName;

    // SmartTAS / Hikvision / WiseEyeMix3
    @Column(length = 30)
    private String sourceSystem;
}
