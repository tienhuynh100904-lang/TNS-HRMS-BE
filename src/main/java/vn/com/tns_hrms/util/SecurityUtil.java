package vn.com.tns_hrms.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.util.Base64;

import vn.com.tns_hrms.dto.response.ResClaimAccount;
import vn.com.tns_hrms.dto.response.ResLogin;

@Service
public class SecurityUtil {
    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    // Distinguishes refresh tokens from access tokens, so one can never be used in place of the other.
    public static final String TOKEN_TYPE_CLAIM = "token_type";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;

    @Value("${jwt.secret.base64}")
    private String jwtKey;

    @Value("${jwt.access-token-validity-in-seconds}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpiration;

    public SecurityUtil(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public static SecretKey toSecretKey(String base64Key) {
        byte[] keyBytes = Base64.from(base64Key).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }

    public Jwt checkValidRefreshToken(String token) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withSecretKey(toSecretKey(jwtKey))
                .macAlgorithm(JWT_ALGORITHM)
                .build();

        Jwt jwt = jwtDecoder.decode(token);
        if (!TOKEN_TYPE_REFRESH.equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
            throw new BadJwtException("Invalid token: not a refresh token");
        }
        return jwt;
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    public static Optional<String> getCurrentUserLogin() {
        return Optional.ofNullable(extractPrincipal(SecurityContextHolder.getContext().getAuthentication()));
    }

    public String createAccessToken(String email, ResLogin dto) {
        ResClaimAccount claimAccount = new ResClaimAccount();
        claimAccount.setEmail(dto.getAccount().getEmail());
        claimAccount.setName(dto.getAccount().getUsername());
        claimAccount.setRole(dto.getAccount().getRole());

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(this.accessTokenExpiration, ChronoUnit.SECONDS))
                .subject(email)
                .claim("account", claimAccount)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public String createRefreshToken(String email, ResLogin dto) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(this.refreshTokenExpiration, ChronoUnit.SECONDS))
                .subject(email)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
