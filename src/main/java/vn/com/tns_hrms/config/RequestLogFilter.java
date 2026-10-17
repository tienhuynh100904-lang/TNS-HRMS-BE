package vn.com.tns_hrms.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * One log line when an API is called and one when it has answered (status and time), so a call can be followed in
 * the terminal while testing, including a long one such as the pull. Runs before Spring Security, so rejected calls
 * (401) show up too. Only the method, path, query and caller address are logged: never headers (the token) or body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    // browsers send an OPTIONS call before every cross-origin call, that is only noise
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String call = request.getMethod() + " " + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        log.info("API call   {} from {}", call, request.getRemoteAddr());
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("API answer {} -> {} in {} ms", call, response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }
}
