package vn.com.tns_hrms.config.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import vn.com.tns_hrms.config.SecurityConfiguration;
import vn.com.tns_hrms.config.ratelimit.RateLimiter.Decision;
import vn.com.tns_hrms.dto.ApiResponse;

/**
 * Decides which limit a call falls under and answers 429 when it is over. Used by two filters: one before the
 * caller is known (token endpoint and per-IP), one after the access token has been checked (per client).
 */
@Component
public class RateLimitService {
    static final String PULL_PATH = "/api/v1/attendance/pull";
    // a client id is short; a longer one is garbage from someone trying to fill the memory
    private static final int MAX_CLIENT_ID_LENGTH = 128;

    private final RateLimitProperties properties;
    private final ObjectMapper mapper;
    private final RateLimiter tokenPerIp;
    private final RateLimiter tokenPerClient;
    private final RateLimiter apiPerIp;
    private final RateLimiter apiPerClient;
    private final RateLimiter pull;

    public RateLimitService(RateLimitProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        int maxKeys = properties.getMaxKeys();
        this.tokenPerIp = new RateLimiter(properties.getTokenPerIp(), maxKeys);
        this.tokenPerClient = new RateLimiter(properties.getTokenPerClient(), maxKeys);
        this.apiPerIp = new RateLimiter(properties.getApiPerIp(), maxKeys);
        this.apiPerClient = new RateLimiter(properties.getApiPerClient(), maxKeys);
        this.pull = new RateLimiter(properties.getPull(), maxKeys);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** Limits that need no identity: the token endpoint (per IP, then per client id) and every other call per IP. */
    public Decision checkBeforeAuthentication(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!isTokenRequest(request)) {
            return apiPerIp.tryAcquire(ip);
        }
        Decision decision = tokenPerIp.tryAcquire(ip);
        if (!decision.allowed()) {
            return decision;
        }
        String clientId = tokenClientId(request);
        return clientId == null ? decision : tokenPerClient.tryAcquire(clientId);
    }

    /** Limits of an authenticated client: all its calls, and the pull on top of that. */
    public Decision checkClient(HttpServletRequest request, String clientId) {
        Decision decision = apiPerClient.tryAcquire(clientId);
        if (decision.allowed() && "POST".equals(request.getMethod()) && PULL_PATH.equals(path(request))) {
            return pull.tryAcquire(clientId);
        }
        return decision;
    }

    public void reject(HttpServletResponse response, Decision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Object> body = ApiResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message("Too many requests, try again in " + decision.retryAfterSeconds() + " seconds")
                .build();
        mapper.writeValue(response.getWriter(), body);
    }

    @Scheduled(fixedDelay = 60_000)
    void evictIdleKeys() {
        tokenPerIp.evictIdle();
        tokenPerClient.evictIdle();
        apiPerIp.evictIdle();
        apiPerClient.evictIdle();
        pull.evictIdle();
    }

    private static boolean isTokenRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && SecurityConfiguration.TOKEN_ENDPOINT.equals(path(request));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /**
     * The client id the caller claims on the token endpoint (Basic header, or client_id in the form), lower-cased
     * because the database does not tell "Admin" and "admin" apart, so the limit must not either.
     */
    private static String tokenClientId(HttpServletRequest request) {
        String clientId = null;
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                clientId = colon < 0 ? decoded : decoded.substring(0, colon);
            } catch (IllegalArgumentException e) {
                // not valid base64: the authorization server rejects it, there is no client id to count
            }
        } else {
            clientId = request.getParameter("client_id");
        }
        if (clientId == null || clientId.isBlank() || clientId.length() > MAX_CLIENT_ID_LENGTH) {
            return null;
        }
        return clientId.trim().toLowerCase(Locale.ROOT);
    }
}
