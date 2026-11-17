package org.example.launcher.infrastructure.download;

import org.example.launcher.domain.port.RetryPolicy;

/**
 * Фиксированное число попыток для скачивания файлов.
 */
public final class FixedRetryPolicy implements RetryPolicy {

    /** Значение по умолчанию для всего лаунчера. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final int maxAttempts;

    public FixedRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS);
    }

    public FixedRetryPolicy(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }
}
