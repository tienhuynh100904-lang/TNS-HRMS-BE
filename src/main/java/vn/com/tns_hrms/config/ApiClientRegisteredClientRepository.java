package vn.com.tns_hrms.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import vn.com.tns_hrms.entity.ApiClient;
import vn.com.tns_hrms.repository.ApiClientRepository;

/**
 * Lets the authorization server read its clients from the api_clients table. Only the client credentials grant
 * exists, and a deactivated client is simply not found, so it can no longer get a token. Clients are created and
 * changed through ApiClientService, never through this repository.
 */
@Component
public class ApiClientRegisteredClientRepository implements RegisteredClientRepository {
    private final ApiClientRepository clientRepository;
    private final long accessTokenValiditySeconds;

    public ApiClientRegisteredClientRepository(ApiClientRepository clientRepository,
            @Value("${app.oauth2.access-token-validity-in-seconds}") long accessTokenValiditySeconds) {
        this.clientRepository = clientRepository;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Clients are managed through /api/v1/clients");
    }

    // the id of a registered client is its client id
    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return clientRepository.findByClientId(clientId).filter(ApiClient::isActive).map(this::toRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient toRegisteredClient(ApiClient client) {
        return RegisteredClient.withId(client.getClientId())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientName(client.getName() == null ? client.getClientId() : client.getName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(accessTokenValiditySeconds))
                        .build())
                .build();
    }
}
