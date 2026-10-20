package vn.com.tns_hrms.config;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/**
 * Keeps nothing. The authorization server normally remembers every token it issues (to introspect, revoke or
 * refresh it), but here the only grant is client credentials: tokens are self-contained JWTs checked by their
 * signature and expiry, and there is no introspection, revocation or refresh endpoint. The default in-memory
 * service would keep one record per issued token forever, so memory would grow with every token request.
 */
@Component
public class StatelessOAuth2AuthorizationService implements OAuth2AuthorizationService {

    @Override
    public void save(OAuth2Authorization authorization) {
        // nothing to remember
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        // nothing was remembered
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return null;
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return null;
    }
}
