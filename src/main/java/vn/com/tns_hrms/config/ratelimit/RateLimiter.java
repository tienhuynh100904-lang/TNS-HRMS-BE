package vn.com.tns_hrms.config.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * One token-bucket limit applied per key (an IP, a client id). Kept in memory, so it counts per application
 * instance: with several instances behind a load balancer each one allows the full rate.
 */
public class RateLimiter {
    /** The answer for one call; {@code retryAfterSeconds} is 0 when the call is allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double capacity;
    private final double tokensPerNano;
    private final int maxKeys;
    private final LongSupplier nanoClock;

    public RateLimiter(RateLimitProperties.Limit limit, int maxKeys) {
        this(limit, maxKeys, System::nanoTime);
    }

    RateLimiter(RateLimitProperties.Limit limit, int maxKeys, LongSupplier nanoClock) {
        if (limit.getBurst() < 1 || limit.getPerMinute() < 1) {
            throw new IllegalArgumentException("A rate limit needs a burst and a rate per minute of at least 1");
        }
        this.capacity = limit.getBurst();
        this.tokensPerNano = limit.getPerMinute() / 60_000_000_000.0;
        this.maxKeys = maxKeys;
        this.nanoClock = nanoClock;
    }

    public Decision tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxKeys) {
                evictIdle();
                if (buckets.size() >= maxKeys) {
                    // memory is protected first: a new key is let through untracked rather than growing the map
                    return Decision.ALLOWED;
                }
            }
            bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        }
        return bucket.take(now, capacity, tokensPerNano);
    }

    /** Drops the keys whose bucket is full again: forgetting them changes nothing for their next call. */
    public void evictIdle() {
        long now = nanoClock.getAsLong();
        buckets.values().removeIf(b -> b.isFull(now, capacity, tokensPerNano));
    }

    int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        synchronized Decision take(long now, double capacity, double tokensPerNano) {
            refill(now, capacity, tokensPerNano);
            if (tokens >= 1) {
                tokens -= 1;
                return Decision.ALLOWED;
            }
            long seconds = (long) Math.ceil((1 - tokens) / tokensPerNano / 1_000_000_000.0);
            return new Decision(false, Math.max(1, seconds));
        }

        synchronized boolean isFull(long now, double capacity, double tokensPerNano) {
            refill(now, capacity, tokensPerNano);
            return tokens >= capacity;
        }

        private void refill(long now, double capacity, double tokensPerNano) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
            lastRefill = now;
        }
    }
}
