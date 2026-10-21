package vn.com.tns_hrms.config;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.web.filter.CorsFilter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import vn.com.tns_hrms.config.ratelimit.ClientRateLimitFilter;
import vn.com.tns_hrms.config.ratelimit.RateLimitFilter;
import vn.com.tns_hrms.config.ratelimit.RateLimitService;
import vn.com.tns_hrms.util.SecurityUtil;

/**
 * The application is its own OAuth2 authorization server (client credentials grant only, POST /oauth2/token) and a
 * resource server that accepts the access tokens it issued. There are no users and no sessions.
 */
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {
    public static final String TOKEN_ENDPOINT = "/oauth2/token";

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final RateLimitService rateLimitService;

    @Value("${jwt.secret.base64}")
    private String jwtKey;

    public SecurityConfiguration(CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
            RateLimitService rateLimitService) {
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
        this.rateLimitService = rateLimitService;
    }

    // hashes the client secrets, and is also the encoder the authorization server checks them with
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The token endpoint only: every other authorization server endpoint stays closed. */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(TOKEN_ENDPOINT)
                .with(new OAuth2AuthorizationServerConfigurer(), Customizer.withDefaults())
                // first of the chain: a guessed secret must be stopped before the client is even checked
                .addFilterBefore(new RateLimitFilter(rateLimitService), DisableEncodeUrlFilter.class)
                .csrf(c -> c.disable())
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] whiteList = {
                "/",
                "/error",
                "/actuator/health"
        };

        http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                // per IP right after CORS (so a 429 carries the CORS headers), per client once the token is checked
                .addFilterAfter(new RateLimitFilter(rateLimitService), CorsFilter.class)
                .addFilterAfter(new ClientRateLimitFilter(rateLimitService), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(whiteList).permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.disable())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(customAuthenticationEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    private SecretKey getSecretKey() {
        return SecurityUtil.toSecretKey(jwtKey);
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
                .withSecretKey(getSecretKey())
                .macAlgorithm(SecurityUtil.JWT_ALGORITHM)
                .build();
    }

    // the authorization server signs with RS256 unless told otherwise: sign with the same HS512 key the decoder checks
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getJwsHeader().algorithm(SecurityUtil.JWT_ALGORITHM);
                context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
            }
        };
    }
}
