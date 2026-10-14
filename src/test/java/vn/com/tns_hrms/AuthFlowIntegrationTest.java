package vn.com.tns_hrms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)))
                .andReturn();
    }

    private String adminAccessToken() throws Exception {
        MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("access_token").asString();
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ADMIN_EMAIL, "wrong-password")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminCanLoginAndCallProtectedEndpoints() throws Exception {
        MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus());
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie != null && setCookie.contains("refreshToken="));
        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains("refresh"));

        String token = adminAccessToken();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.role.name").value("ADMIN"));
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.total").value(1));
        mockMvc.perform(get("/api/v1/roles/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/permissions/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutGrantedPermissionIsForbidden() throws Exception {
        String adminToken = adminAccessToken();

        MvcResult roles = mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + adminToken))
                .andReturn();
        long userRoleId = 0;
        for (JsonNode role : objectMapper.readTree(roles.getResponse().getContentAsString())
                .get("data").get("result")) {
            if ("USER".equals(role.get("name").asString())) {
                userRoleId = role.get("id").asLong();
            }
        }

        mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"staff@test.local\",\"password\":\"secret1\",\"username\":\"Staff\",\"roleId\":"
                        + userRoleId + "}"))
                .andExpect(status().isCreated());

        MvcResult staffLogin = login("staff@test.local", "secret1");
        String staffToken = objectMapper.readTree(staffLogin.getResponse().getContentAsString())
                .get("data").get("access_token").asString();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String refreshToken = setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty());

        // An access token is not a valid refresh token.
        String accessToken = adminAccessToken();
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", accessToken)))
                .andExpect(status().isUnauthorized());
    }
}
