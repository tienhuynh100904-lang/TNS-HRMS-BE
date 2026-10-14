package vn.com.tns_hrms.service;

import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.com.tns_hrms.dto.response.ResAccount;
import vn.com.tns_hrms.dto.response.ResLogin;
import vn.com.tns_hrms.entity.Account;
import vn.com.tns_hrms.exception.BadRequestException;
import vn.com.tns_hrms.exception.ResourceNotFoundException;
import vn.com.tns_hrms.helper.EmailHelper;
import vn.com.tns_hrms.mapper.AccountMapper;
import vn.com.tns_hrms.repository.AccountRepository;
import vn.com.tns_hrms.util.SecurityUtil;

@Service
public class AuthenticationService {
    private static final long VERIFICATION_CODE_VALIDITY_SECONDS = 300;

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtil securityUtil;
    private final AccountMapper accountMapper;
    private final EmailHelper emailHelper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            SecurityUtil securityUtil,
            AccountMapper accountMapper,
            AuthenticationManager authenticationManager,
            EmailHelper emailHelper) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityUtil = securityUtil;
        this.accountMapper = accountMapper;
        this.authenticationManager = authenticationManager;
        this.emailHelper = emailHelper;
    }

    @Transactional(readOnly = true)
    public ResLogin login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            throw new ResourceNotFoundException("Account not found");
        }
        return buildLogin(account);
    }

    @Transactional(readOnly = true)
    public ResLogin refreshToken(String refreshToken) {
        Jwt decodedToken = securityUtil.checkValidRefreshToken(refreshToken);

        Account account = accountRepository.findByEmail(decodedToken.getSubject());
        if (account == null || !account.isActive()) {
            throw new ResourceNotFoundException("Account not found");
        }
        return buildLogin(account);
    }

    @Transactional(readOnly = true)
    public ResAccount.Full fetchAccountByEmail(String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            return null;
        }
        return accountMapper.toFullDTO(account);
    }

    public void logout() {
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public void sendVerificationEmail(String email) {
        Account account = accountRepository.findByEmail(email);
        // Do not reveal whether the email is registered.
        if (account == null || !account.isActive()) {
            return;
        }

        if (account.getCodeExpired() != null && account.getCodeExpired().isAfter(Instant.now())) {
            throw new BadRequestException("The code is still valid. You can only resubmit it after it expires.");
        }

        String verificationCode = generateVerificationCode();
        account.setCodeId(verificationCode);
        account.setCodeExpired(Instant.now().plusSeconds(VERIFICATION_CODE_VALIDITY_SECONDS));
        accountRepository.save(account);

        emailHelper.sendEmailFromTemplate(
                email,
                "Mã xác thực khôi phục mật khẩu - TNS HRMS",
                "forgot-password-email",
                account.getUsername() != null ? account.getUsername() : "Quý khách",
                verificationCode);
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            throw new BadRequestException("Invalid verification code");
        }

        if (account.getCodeId() == null || account.getCodeExpired() == null) {
            throw new BadRequestException("No verification code found. Please request a new code.");
        }
        if (!account.getCodeId().equals(code)) {
            throw new BadRequestException("Invalid verification code");
        }
        if (account.getCodeExpired().isBefore(Instant.now())) {
            throw new BadRequestException("Verification code has expired. Please request a new code.");
        }
        if (!isPasswordStrong(newPassword)) {
            throw new BadRequestException(
                    "Password must be at least 8 characters long and contain at least one uppercase letter, "
                            + "one lowercase letter, one digit, and one special character");
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        account.setCodeId(null);
        account.setCodeExpired(null);
        accountRepository.save(account);
    }

    private ResLogin buildLogin(Account account) {
        ResLogin.AccountLogin accountLogin = new ResLogin.AccountLogin();
        accountLogin.setId(account.getId());
        accountLogin.setEmail(account.getEmail());
        accountLogin.setUsername(account.getUsername());
        accountLogin.setAvatar(account.getAvatar());
        accountLogin.setPhone(account.getPhone());
        accountLogin.setRole(account.getRole() != null ? account.getRole().getName() : null);

        ResLogin resLogin = new ResLogin();
        resLogin.setAccount(accountLogin);
        resLogin.setAccessToken(securityUtil.createAccessToken(account.getEmail(), resLogin));
        resLogin.setRefreshToken(securityUtil.createRefreshToken(account.getEmail(), resLogin));
        return resLogin;
    }

    private String generateVerificationCode() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecialChar = true;
            }
        }

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar;
    }
}
