package vn.com.tns_hrms.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResLogin {
    @JsonProperty("access_token")
    private String accessToken;

    // Sent to the client as an HttpOnly cookie, never in the response body.
    @JsonIgnore
    private String refreshToken;
    private AccountLogin account;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountLogin {
        private long id;
        private String email;
        private String username;
        private String avatar;
        private String phone;
        private String role;
    }
}
