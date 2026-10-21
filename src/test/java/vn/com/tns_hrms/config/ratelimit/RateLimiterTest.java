package vn.com.tns_hrms.config.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import vn.com.tns_hrms.config.ratelimit.RateLimiter.Decision;

class RateLimiterTest {
    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    private RateLimiter limiter(int burst, int perMinute, int maxKeys) {
        return new RateLimiter(new RateLimitProperties.Limit(burst, perMinute), maxKeys, now::get);
    }

    private void advanceSeconds(long seconds) {
        now.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    @Test
    void allowsTheBurstThenRejectsWithTheWaitUntilTheNextToken() {
        RateLimiter limiter = limiter(3, 6, 100); // one token back every 10 seconds
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("a").allowed());
        }
        Decision rejected = limiter.tryAcquire("a");
        assertFalse(rejected.allowed());
        assertEquals(10, rejected.retryAfterSeconds());

        advanceSeconds(4);
        assertEquals(6, limiter.tryAcquire("a").retryAfterSeconds());
    }

    @Test
    void tokensComeBackWithTimeButNeverAboveTheBurst() {
        RateLimiter limiter = limiter(2, 6, 100);
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertFalse(limiter.tryAcquire("a").allowed());

        advanceSeconds(10);
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());

        advanceSeconds(3600); // a long pause refills to the burst, not to an hour of credit
        assertTrue(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());
    }

    @Test
    void everyKeyHasItsOwnBucket() {
        RateLimiter limiter = limiter(1, 1, 100);
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("b").allowed());
    }

    @Test
    void idleKeysAreForgottenButBusyOnesAreKept() {
        RateLimiter limiter = limiter(2, 60, 100);
        limiter.tryAcquire("idle");
        limiter.tryAcquire("busy");
        limiter.tryAcquire("busy");
        advanceSeconds(1); // "idle" has its token back (full again), "busy" has one of its two

        limiter.evictIdle();
        assertEquals(1, limiter.size());
        assertTrue(limiter.tryAcquire("busy").allowed()); // its count survived: only the one token is left
        assertFalse(limiter.tryAcquire("busy").allowed());
    }

    @Test
    void whenTheMapIsFullANewKeyIsLetThroughWithoutBeingTracked() {
        RateLimiter limiter = limiter(1, 1, 2);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");

        assertTrue(limiter.tryAcquire("c").allowed());
        assertTrue(limiter.tryAcquire("c").allowed(), "not tracked, so never limited");
        assertEquals(2, limiter.size());

        advanceSeconds(120); // a and b are idle again: room for c
        assertTrue(limiter.tryAcquire("c").allowed());
        assertFalse(limiter.tryAcquire("c").allowed());
    }

    @Test
    void aLimitWithoutBurstOrRateIsRefusedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> limiter(0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> limiter(10, 0, 10));
    }
}
