package vn.com.tns_hrms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The editable data of an employee. An update replaces all of it: a code or department left out (or sent empty) is
 * cleared, so send the whole employee.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReqEmployee {
    private long id;

    @Size(max = 20, message = "Employee code must not exceed 20 characters")
    private String employeeCode;

    @Size(max = 20, message = "User code must not exceed 20 characters")
    private String userCode;

    @Size(max = 20, message = "Card code must not exceed 20 characters")
    private String cardCode;

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    // null for no department
    private Long departmentId;

    // required, so that leaving it out can never deactivate an employee by accident
    @NotNull(message = "Active is required")
    private Boolean active;
}
