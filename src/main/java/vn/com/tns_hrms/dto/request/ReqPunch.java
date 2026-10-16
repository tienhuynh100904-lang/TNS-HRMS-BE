package vn.com.tns_hrms.dto.request;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.tns_hrms.entity.PunchRaw;

/** One punch as read from a device, before it is stored in punch_raw. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReqPunch {
    // code of the person on the device (ZK: user_id / uid), stored as the user code
    private String userCode;
    private LocalDateTime punchTime;
    // the device record id, tracing only
    private String sourceRef;
    private Integer verifyType;
    // null when the device does not report whether the punch is an in or an out
    private PunchRaw.Direction direction;

    public ReqPunch(String userCode, LocalDateTime punchTime, String sourceRef, Integer verifyType) {
        this(userCode, punchTime, sourceRef, verifyType, null);
    }
}
