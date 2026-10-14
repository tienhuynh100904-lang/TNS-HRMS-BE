package vn.com.tns_hrms.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.com.tns_hrms.entity.Account;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.exception.PermissionException;
import vn.com.tns_hrms.service.AccountService;
import vn.com.tns_hrms.util.SecurityUtil;

/**
 * Authorises each authenticated request against the permissions (apiPath + HTTP method) granted to the
 * caller's role. ADMIN bypasses the check.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private static final String DENIED = "You do not have permission to access this endpoint";

    private final AccountService accountService;

    public PermissionInterceptor(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            // Unauthenticated requests are rejected by the security filter chain unless the path is whitelisted.
            return true;
        }

        Account account = this.accountService.getUserByEmail(email);
        if (account == null || !account.isActive()) {
            throw new PermissionException(DENIED);
        }

        Role role = account.getRole();
        if (role == null || !role.isActive()) {
            throw new PermissionException(DENIED);
        }
        if (AccountService.ADMIN_ROLE.equals(role.getName())) {
            return true;
        }

        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String httpMethod = request.getMethod();

        boolean allowed = role.getPermissions().stream().anyMatch(permission -> permission.isActive()
                && permission.getApiPath().equals(path)
                && permission.getMethod().equals(httpMethod));
        if (!allowed) {
            throw new PermissionException(DENIED);
        }
        return true;
    }
}
