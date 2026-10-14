package vn.com.tns_hrms.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.com.tns_hrms.dto.ApiResponse;
import vn.com.tns_hrms.dto.request.ReqForgotPassword;
import vn.com.tns_hrms.dto.request.ReqLogin;
import vn.com.tns_hrms.dto.request.ReqResetPassword;
import vn.com.tns_hrms.dto.response.ResAccount;
import vn.com.tns_hrms.dto.response.ResLogin;
import vn.com.tns_hrms.exception.BadRequestException;
import vn.com.tns_hrms.service.AuthenticationService;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthenticationController {
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthenticationService authenticationService;

    @Value("${jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpiration;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<ResLogin>> login(@Valid @RequestBody ReqLogin request) {
        ResLogin loginData = authenticationService.login(request.getEmail(), request.getPassword());
        return loginResponse(loginData, "Login successful");
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ResAccount.Full>> getAccount() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        ApiResponse<ResAccount.Full> response = ApiResponse.<ResAccount.Full>builder()
                .status(HttpStatus.OK.value())
                .message("Account information retrieved successfully")
                .data(authenticationService.fetchAccountByEmail(email))
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<ResLogin>> refreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BadRequestException("Refresh token not found in cookie");
        }

        ResLogin loginData = authenticationService.refreshToken(refreshToken);
        return loginResponse(loginData, "Token refreshed successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authenticationService.logout();

        ApiResponse<Void> res = ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Logout successful")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie("", 0).toString())
                .body(res);
    }

    @PostMapping("/send-verification-email")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(@Valid @RequestBody ReqForgotPassword request) {
        authenticationService.sendVerificationEmail(request.getEmail());

        ApiResponse<Void> res = ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("If the email is registered, a verification code has been sent")
                .build();
        return ResponseEntity.ok(res);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ReqResetPassword request) {
        authenticationService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());

        ApiResponse<Void> res = ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Password reset successfully")
                .build();
        return ResponseEntity.ok(res);
    }

    // The refresh token travels only in an HttpOnly cookie, so it is removed from the response body.
    private ResponseEntity<ApiResponse<ResLogin>> loginResponse(ResLogin loginData, String message) {
        ResponseCookie cookie = refreshTokenCookie(loginData.getRefreshToken(), refreshTokenExpiration);
        loginData.setRefreshToken(null);

        ApiResponse<ResLogin> res = ApiResponse.<ResLogin>builder()
                .status(HttpStatus.OK.value())
                .message(message)
                .data(loginData)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(res);
    }

    private ResponseCookie refreshTokenCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("None")
                .build();
    }
}
