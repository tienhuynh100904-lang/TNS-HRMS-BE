package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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

    private String adminToken() throws Exception {
        return TestAuth.adminToken(mockMvc, objectMapper);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long roleId(String adminToken, String name) throws Exception {
        MvcResult roles = mockMvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + adminToken))
                .andReturn();
        for (JsonNode role : json(roles).get("data").get("result")) {
            if (name.equals(role.get("name").asString())) {
                return role.get("id").asLong();
            }
        }
        throw new AssertionError("no role " + name);
    }

    /** Creates a client through the API and returns its answer (id, clientId and the secret shown once). */
    private JsonNode createClient(String adminToken, String roleName) throws Exception {
        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + clientId + "\",\"name\":\"Test\",\"roleId\":"
                        + roleId(adminToken, roleName) + "}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("data");
    }

    private int tokenStatus(String clientId, String secret) throws Exception {
        return TestAuth.tokenRequest(mockMvc, clientId, secret).getResponse().getStatus();
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void clientGetsABearerTokenSignedWithTheApplicationKey() throws Exception {
        MvcResult result = TestAuth.tokenRequest(mockMvc, TestAuth.ADMIN_CLIENT_ID, TestAuth.ADMIN_CLIENT_SECRET);
        assertEquals(200, result.getResponse().getStatus());

        JsonNode body = json(result);
        assertEquals("Bearer", body.get("token_type").asString());
        assertEquals(3600, body.get("expires_in").asLong());
        assertFalse(body.has("refresh_token"), "client credentials never returns a refresh token");

        String[] parts = body.get("access_token").asString().split("\\.");
        JsonNode header = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        JsonNode claims = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertEquals("HS512", header.get("alg").asString());
        assertEquals(TestAuth.ADMIN_CLIENT_ID, claims.get("sub").asString());
        assertEquals(TestAuth.ADMIN_CLIENT_ID, claims.get("client_id").asString());
    }

    @Test
    void clientCanAlsoSendItsCredentialsInTheBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("client_id", TestAuth.ADMIN_CLIENT_ID)
                .param("client_secret", TestAuth.ADMIN_CLIENT_SECRET)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
    }

    @Test
    void wrongSecretOrUnknownClientIsRejected() throws Exception {
        assertEquals(401, tokenStatus(TestAuth.ADMIN_CLIENT_ID, "wrong-secret"));
        assertEquals(401, tokenStatus("nobody", "whatever"));
        mockMvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheClientCredentialsGrantExists() throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                (TestAuth.ADMIN_CLIENT_ID + ":" + TestAuth.ADMIN_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        for (String grant : new String[] { "password", "refresh_token", "authorization_code" }) {
            int code = mockMvc.perform(post("/oauth2/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", grant)).andReturn().getResponse().getStatus();
            assertEquals(400, code, grant);
        }
    }

    @Test
    void theOldLoginAndEveryOtherAuthorizationServerEndpointAreGone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.c\",\"password\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/oauth2/authorize?response_type=code&client_id=admin-client"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminClientCanCallProtectedEndpoints() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/permissions/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String token = adminToken();
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 4) + "AAAA";
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientWithoutGrantedPermissionIsForbidden() throws Exception {
        String adminToken = adminToken();
        JsonNode created = createClient(adminToken, "USER");

        String clientToken = TestAuth.accessToken(mockMvc, objectMapper, created.get("clientId").asString(),
                created.get("clientSecret").asString());
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void secretIsShownOnceAndRenewingItStopsTheOldOne() throws Exception {
        String adminToken = adminToken();
        JsonNode created = createClient(adminToken, "USER");
        String clientId = created.get("clientId").asString();
        String firstSecret = created.get("clientSecret").asString();
        assertTrue(firstSecret.length() >= 40);
        assertEquals(200, tokenStatus(clientId, firstSecret));

        MvcResult detail = mockMvc.perform(get("/api/v1/clients/" + created.get("id").asLong())
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk()).andReturn();
        assertFalse(detail.getResponse().getContentAsString().contains("ecret"), "the secret is never listed again");

        MvcResult renewed = mockMvc.perform(post("/api/v1/clients/" + created.get("id").asLong() + "/secret")
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk()).andReturn();
        String newSecret = json(renewed).get("data").get("clientSecret").asString();
        assertEquals(401, tokenStatus(clientId, firstSecret));
        assertEquals(200, tokenStatus(clientId, newSecret));
    }

    @Test
    void deactivatedClientLosesAccessAtOnceEvenWithAnUnexpiredToken() throws Exception {
        String adminToken = adminToken();
        JsonNode created = createClient(adminToken, "ADMIN");
        String clientId = created.get("clientId").asString();
        String secret = created.get("clientSecret").asString();
        String token = TestAuth.accessToken(mockMvc, objectMapper, clientId, secret);
        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/clients").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + created.get("id").asLong() + ",\"name\":\"Test\",\"roleId\":"
                        + roleId(adminToken, "ADMIN") + ",\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertEquals(401, tokenStatus(clientId, secret));
    }

    @Test
    void theLastActiveAdminClientCannotBeDeactivated() throws Exception {
        String adminToken = adminToken();
        MvcResult list = mockMvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + adminToken))
                .andReturn();
        long adminId = -1;
        int admins = 0;
        for (JsonNode client : json(list).get("data").get("result")) {
            if ("ADMIN".equals(client.get("role").asString()) && client.get("active").asBoolean()) {
                admins++;
                if (TestAuth.ADMIN_CLIENT_ID.equals(client.get("clientId").asString())) {
                    adminId = client.get("id").asLong();
                }
            }
        }
        assertEquals(1, admins, "only the seeded admin client is active");

        mockMvc.perform(put("/api/v1/clients").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + adminId + ",\"name\":\"Administrator\",\"roleId\":"
                        + roleId(adminToken, "USER") + ",\"active\":true}"))
                .andExpect(status().isBadRequest());
    }
}
