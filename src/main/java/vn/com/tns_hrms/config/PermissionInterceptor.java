package vn.com.tns_hrms.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.com.tns_hrms.entity.ApiClient;
import vn.com.tns_hrms.entity.Role;
import vn.com.tns_hrms.exception.PermissionException;
import vn.com.tns_hrms.service.ApiClientService;
import vn.com.tns_hrms.util.SecurityUtil;

/**
 * Authorises each authenticated request against the permissions (apiPath + HTTP method) granted to the
 * role of the calling client. ADMIN bypasses the check. The client is read on every request, so deactivating a
 * client or changing its role takes effect at once, even for access tokens that have not expired yet.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private static final String DENIED = "You do not have permission to access this endpoint";

    private final ApiClientService clientService;

    public PermissionInterceptor(ApiClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientId = SecurityUtil.getCurrentUserLogin().orElse("");
        if (clientId.isEmpty()) {
            // Unauthenticated requests are rejected by the security filter chain unless the path is whitelisted.
            return true;
        }

        ApiClient client = this.clientService.getByClientId(clientId);
        if (client == null || !client.isActive()) {
            throw new PermissionException(DENIED);
        }

        Role role = client.getRole();
        if (role == null || !role.isActive()) {
            throw new PermissionException(DENIED);
        }
        if (Role.ADMIN.equals(role.getName())) {
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
