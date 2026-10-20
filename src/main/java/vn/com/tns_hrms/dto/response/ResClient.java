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
public class ResClient {
    private long id;
    private String clientId;
    private String name;
    private String description;
    private boolean active;
    private String role;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    /** The answer to creating a client or renewing its secret: the only time the secret is ever shown. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class WithSecret extends ResClient {
        private String clientSecret;
    }
}
