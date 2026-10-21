package vn.com.tns_hrms.config.ratelimit;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.com.tns_hrms.config.ratelimit.RateLimiter.Decision;

/**
 * Per-client limits, applied once the access token has been checked, so the key is the client id of the token and
 * a client cannot dodge its limit by changing address. Added to the security filter chain (not registered as a
 * bean) right after the bearer token filter, which is before the permission check, so a forbidden call still counts.
 */
public class ClientRateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService service;

    public ClientRateLimitFilter(RateLimitService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !service.isEnabled() || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            Decision decision = service.checkClient(request, jwt.getName());
            if (!decision.allowed()) {
                service.reject(response, decision);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
