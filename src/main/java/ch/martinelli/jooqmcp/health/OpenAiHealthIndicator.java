package ch.martinelli.jooqmcp.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator that tracks OpenAI API quota status.
 * When a quota error is detected, health goes DOWN so that
 * external monitors (e.g., UptimeRobot) can trigger alerts.
 */
@Component
public class OpenAiHealthIndicator implements HealthIndicator {

    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> errorSince = new AtomicReference<>();

    @Override
    public Health health() {
        String error = lastError.get();
        if (error != null) {
            return Health.down()
                    .withDetail("error", error)
                    .withDetail("since", errorSince.get().toString())
                    .build();
        }
        return Health.up().build();
    }

    public void reportQuotaExceeded(String message) {
        lastError.set(message);
        errorSince.compareAndSet(null, Instant.now());
    }

    public void reportHealthy() {
        lastError.set(null);
        errorSince.set(null);
    }
}
