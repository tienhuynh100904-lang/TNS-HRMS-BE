package vn.com.tns_hrms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gets access tokens the way a real client does: the OAuth2 client credentials grant on POST /oauth2/token. */
final class TestAuth {
    static final String ADMIN_CLIENT_ID = "admin-client";
    static final String ADMIN_CLIENT_SECRET = "Admin-Client-Secret-12345";

    private TestAuth() {
    }

    /** The raw answer of the token endpoint, with the client sending its credentials in the Basic header. */
    static MvcResult tokenRequest(MockMvc mockMvc, String clientId, String secret) throws Exception {
        String basic = java.util.Base64.getEncoder()
                .encodeToString((clientId + ":" + secret).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return mockMvc.perform(post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")).andReturn();
    }

    static String accessToken(MockMvc mockMvc, ObjectMapper objectMapper, String clientId, String secret)
            throws Exception {
        MvcResult result = tokenRequest(mockMvc, clientId, secret);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        if (result.getResponse().getStatus() != 200 || !body.hasNonNull("access_token")) {
            throw new IllegalStateException("No token for client " + clientId + ": " + result.getResponse().getStatus()
                    + " " + result.getResponse().getContentAsString());
        }
        return body.get("access_token").asString();
    }

    static String adminToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return accessToken(mockMvc, objectMapper, ADMIN_CLIENT_ID, ADMIN_CLIENT_SECRET);
    }
}
