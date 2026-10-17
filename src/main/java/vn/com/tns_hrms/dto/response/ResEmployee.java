package vn.com.tns_hrms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResEmployee {
    private long id;
    private String employeeCode;
    // code of the person on the attendance devices; null when the employee does not use a device
    private String userCode;
    private String cardCode;
    private String fullName;
    // null when the employee has no department
    private Long departmentId;
    private String departmentName;
    private boolean active;
}
