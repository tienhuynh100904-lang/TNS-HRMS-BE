package vn.com.tns_hrms.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import vn.com.tns_hrms.dto.ApiResponse;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper mapper;

    public CustomAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        this.delegate.commence(request, response, authException);
        response.setContentType("application/json;charset=UTF-8");

        String errorMessage;
        String userMessage;

        Throwable cause = authException.getCause();
        String exceptionMessage = cause != null ? cause.getMessage() : authException.getMessage();

        if (exceptionMessage == null) {
            exceptionMessage = authException.getClass().getSimpleName();
        }

        if (exceptionMessage.contains("Bearer token") && exceptionMessage.contains("supplied")) {
            errorMessage = "Missing token";
            userMessage = "Token not found in Authorization header";
        } else if (exceptionMessage.contains("expired") || exceptionMessage.contains("Jwt expired")) {
            errorMessage = "Token expired";
            userMessage = "Your token has expired, please log in again";
        } else if (exceptionMessage.contains("signature") || exceptionMessage.contains("Signed")) {
            errorMessage = "Invalid signature";
            userMessage = "The token has an invalid signature";
        } else if (exceptionMessage.contains("malformed") || exceptionMessage.contains("Malformed")) {
            errorMessage = "Malformed token";
            userMessage = "The token is not in the correct JWT format";
        } else if (exceptionMessage.contains("Invalid") || exceptionMessage.contains("invalid")) {
            errorMessage = "Invalid token";
            userMessage = "Invalid token";
        } else {
            errorMessage = "Unauthorized";
            userMessage = "Authentication is required to access this resource";
        }

        ApiResponse<Object> res = ApiResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(errorMessage)
                .message(userMessage)
                .build();
        mapper.writeValue(response.getWriter(), res);
    }
}
