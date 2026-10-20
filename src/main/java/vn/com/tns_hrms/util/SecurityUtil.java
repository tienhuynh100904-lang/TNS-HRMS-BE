package vn.com.tns_hrms.util;

import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;

import com.nimbusds.jose.util.Base64;

public final class SecurityUtil {
    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    private SecurityUtil() {
    }

    public static SecretKey toSecretKey(String base64Key) {
        byte[] keyBytes = Base64.from(base64Key).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /** The client id of the caller: the subject of its access token. */
    public static Optional<String> getCurrentUserLogin() {
        return Optional.ofNullable(extractPrincipal(SecurityContextHolder.getContext().getAuthentication()));
    }
}
