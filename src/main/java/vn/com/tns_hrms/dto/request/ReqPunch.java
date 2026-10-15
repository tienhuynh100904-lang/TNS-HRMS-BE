package vn.com.tns_hrms.dto.request;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One punch as read from a device, before it is stored in punch_raw. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReqPunch {
    private String staffCode;
    private LocalDateTime punchTime;
    // the device record id, tracing only
    private String sourceRef;
    private Integer verifyType;
}
