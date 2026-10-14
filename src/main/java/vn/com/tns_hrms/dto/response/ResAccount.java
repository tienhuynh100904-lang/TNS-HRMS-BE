package vn.com.tns_hrms.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResAccount {
    private long id;
    private String email;
    private String username;
    private String phone;
    private String avatar;
    private boolean active;

    @Getter
    @Setter
    public static class Full extends ResAccount {
        private ResRole role;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;
        private String updatedBy;
    }
}
