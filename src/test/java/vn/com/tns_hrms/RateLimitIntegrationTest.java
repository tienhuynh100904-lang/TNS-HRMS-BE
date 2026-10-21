package vn.com.tns_hrms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Small limits and a slow refill, so a limit that is reached stays reached for the whole test. Every test uses its
 * own address and its own client ids because the buckets live as long as the application context.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.token-per-ip.burst=5",
        "app.rate-limit.token-per-ip.per-minute=1",
        "app.rate-limit.token-per-client.burst=3",
        "app.rate-limit.token-per-client.per-minute=1",
        "app.rate-limit.api-per-ip.burst=6",
        "app.rate-limit.api-per-ip.per-minute=1",
        "app.rate-limit.api-per-client.burst=8",
        "app.rate-limit.api-per-client.per-minute=1",
        "app.rate-limit.pull.burst=1",
        "app.rate-limit.pull.per-minute=1"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private final AtomicInteger sequence = new AtomicInteger();

    /** An address no other call used, so the per-IP limits never get in the way of what a test is about. */
    private String nextIp() {
        int n = sequence.getAndIncrement();
        return "10.9." + (n / 250) + "." + (n % 250 + 1);
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String basic(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private int token(String ip, String clientId, String secret) throws Exception {
        return mockMvc.perform(post("/oauth2/token").with(from(ip))
                .header(HttpHeaders.AUTHORIZATION, basic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")).andReturn().getResponse().getStatus();
    }

    private String adminToken(String ip) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token").with(from(ip))
                .header(HttpHeaders.AUTHORIZATION, basic(TestAuth.ADMIN_CLIENT_ID, TestAuth.ADMIN_CLIENT_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("access_token").asString();
    }

    @Test
    void guessingASecretIsStoppedPerClientIdAndAnotherClientIsNotAffected() throws Exception {
        String victim = "victim-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertEquals(401, token("10.0.1.1", victim, "guess-" + i));
        }

        // the 4th try is refused before the secret is even checked, whatever its value
        mockMvc.perform(post("/oauth2/token").with(from("10.0.1.1"))
                .header(HttpHeaders.AUTHORIZATION, basic(victim, "guess-3"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));

        // from here on every call has a new address, so only the limit of the client id can be what answers 429:
        // changing the case of the id does not give a fresh allowance
        assertEquals(429, token("10.0.1.2", victim.toUpperCase(), "guess-4"));
        // and neither does sending the id in the body instead of the header
        mockMvc.perform(post("/oauth2/token").with(from("10.0.1.3"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("client_id", victim).param("client_secret", "guess-5"))
                .andExpect(status().isTooManyRequests());

        // another client id still gets through to the real check (and fails it with a 401)
        assertEquals(401, token("10.0.1.4", "someone-else", "x"));
    }

    @Test
    void oneAddressTryingManyClientIdsIsStoppedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(401, token("10.0.2.1", "spray-" + i, "x"));
        }
        assertEquals(429, token("10.0.2.1", "spray-5", "x"));
        assertEquals(401, token("10.0.2.2", "spray-6", "x"), "another address has its own allowance");
    }

    @Test
    void aValidTokenRequestIsNotLimitedWhileUnderTheLimit() throws Exception {
        assertEquals(200, token("10.0.3.1", TestAuth.ADMIN_CLIENT_ID, TestAuth.ADMIN_CLIENT_SECRET));
    }

    @Test
    void floodOfCallsFromOneAddressIsStoppedBeforeAuthentication() throws Exception {
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/api/v1/clients").with(from("10.0.4.1"))).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/clients").with(from("10.0.4.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        mockMvc.perform(get("/api/v1/clients").with(from("10.0.4.2"))).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckAndPreflightAreNeverLimited() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/actuator/health").with(from("10.0.5.1"))).andExpect(status().isOk());
            mockMvc.perform(options("/api/v1/clients").with(from("10.0.5.1"))
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void limitedAnswerStillCarriesTheCorsHeadersSoABrowserCanReadIt() throws Exception {
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/api/v1/clients").with(from("10.0.6.1"))).andReturn();
        }
        mockMvc.perform(get("/api/v1/clients").with(from("10.0.6.1")).header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString("Retry-After")));
    }

    /** A USER client (no permission anywhere) and its bearer header, made through the API with the admin client. */
    private String newClientBearer() throws Exception {
        String adminToken = adminToken(nextIp());
        long userRoleId = -1;
        MvcResult roles = mockMvc.perform(get("/api/v1/roles").with(from(nextIp()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andReturn();
        for (JsonNode role : objectMapper.readTree(roles.getResponse().getContentAsString()).get("data").get("result")) {
            if ("USER".equals(role.get("name").asString())) {
                userRoleId = role.get("id").asLong();
            }
        }
        assertTrue(userRoleId > 0);

        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/v1/clients").with(from(nextIp()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + clientId + "\",\"name\":\"Test\",\"roleId\":" + userRoleId + "}"))
                .andExpect(status().isCreated()).andReturn();
        String secret = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("clientSecret").asString();

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token").with(from(nextIp()))
                .header(HttpHeaders.AUTHORIZATION, basic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")).andExpect(status().isOk()).andReturn();
        return "Bearer " + objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asString();
    }

    @Test
    void clientIsLimitedByItsTokenWhateverTheAddress() throws Exception {
        String bearer = newClientBearer();
        for (int i = 0; i < 8; i++) {
            // a new address each time: the per-IP limit is not what stops this. The calls are 403 (no permission),
            // and they count all the same because the limit is applied before the permission check.
            mockMvc.perform(get("/api/v1/roles/stats").with(from(nextIp())).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/roles/stats").with(from(nextIp())).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void pullHasItsOwnLowLimitThatTheRestOfTheApiDoesNotShare() throws Exception {
        String bearer = newClientBearer();
        String ip = nextIp();

        mockMvc.perform(post("/api/v1/attendance/pull").with(from(ip)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden()); // no pull permission, which is what makes it safe to repeat
        mockMvc.perform(post("/api/v1/attendance/pull").with(from(ip)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/v1/employees").with(from(ip)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }
}
