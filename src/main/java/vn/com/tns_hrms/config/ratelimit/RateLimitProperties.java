package vn.com.tns_hrms.config.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Settings of the rate limits (app.rate-limit.*). Each limit is a token bucket: it holds up to {@code burst}
 * requests at once and gets {@code perMinute} requests back every minute, so a short burst is fine but a steady
 * flood above the rate is answered with 429.
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private boolean enabled = true;

    // most keys (IPs or client ids) kept in memory per limit; idle ones are dropped every minute
    private int maxKeys = 100_000;

    // POST /oauth2/token: a client asks for a token about once an hour, so these only ever stop password guessing
    private Limit tokenPerIp = new Limit(20, 20);
    private Limit tokenPerClient = new Limit(10, 10);

    // every other call, counted per IP before the caller is known (stops floods and bad-token spam)
    private Limit apiPerIp = new Limit(100, 600);

    // every other call, counted per authenticated client
    private Limit apiPerClient = new Limit(100, 300);

    // POST /api/v1/attendance/pull starts a Python process, so it gets a much lower limit per client
    private Limit pull = new Limit(3, 3);

    @Getter
    @Setter
    public static class Limit {
        private int burst;
        private int perMinute;

        public Limit() {
        }

        public Limit(int burst, int perMinute) {
            this.burst = burst;
            this.perMinute = perMinute;
        }
    }
}
