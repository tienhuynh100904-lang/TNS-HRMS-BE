package vn.com.tns_hrms.config.ratelimit;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.com.tns_hrms.config.ratelimit.RateLimiter.Decision;

/**
 * Per-IP limits, applied before anyone is authenticated: the token endpoint (password guessing) and a flood of
 * calls from one address. Added to the security filter chains (not registered as a bean) right after the CORS
 * filter, so the 429 still carries the CORS headers and a browser can read it.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService service;

    public RateLimitFilter(RateLimitService service) {
        this.service = service;
    }

    // a browser sends an OPTIONS call before every cross-origin call, and a load balancer probes the health check
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !service.isEnabled() || "OPTIONS".equals(request.getMethod())
                || "/actuator/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Decision decision = service.checkBeforeAuthentication(request);
        if (!decision.allowed()) {
            service.reject(response, decision);
            return;
        }
        chain.doFilter(request, response);
    }
}
