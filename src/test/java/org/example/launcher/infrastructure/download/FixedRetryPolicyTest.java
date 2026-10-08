package org.example.launcher.infrastructure.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FixedRetryPolicy")
class FixedRetryPolicyTest {

    @Test
    @DisplayName("default is three attempts")
    void defaultAttempts() {
        assertEquals(3, new FixedRetryPolicy().maxAttempts());
        assertEquals(FixedRetryPolicy.DEFAULT_MAX_ATTEMPTS,
                new FixedRetryPolicy().maxAttempts());
    }

    @Test
    @DisplayName("custom value is kept, minimum is one")
    void customAndClamped() {
        assertEquals(5, new FixedRetryPolicy(5).maxAttempts());
        assertEquals(1, new FixedRetryPolicy(0).maxAttempts());
        assertEquals(1, new FixedRetryPolicy(-10).maxAttempts());
    }

    @Test
    @DisplayName("shouldRetry stops after the last attempt")
    void retryBoundary() {
        var policy = new FixedRetryPolicy(3);
        assertTrue(policy.shouldRetry(1));
        assertTrue(policy.shouldRetry(2));
        assertFalse(policy.shouldRetry(3));
        assertFalse(policy.shouldRetry(4));
    }
}
