package vn.com.tns_hrms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One user as read from a device (password and card number are deliberately not carried over). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReqDeviceUser {
    // the code the device reports in its punch log (user_id), not the internal slot number (uid)
    private String staffCode;
    private String name;
    // 0 = normal user, anything else is a device administrator/manager
    private int privilege;
}
