package io.fluxzero.training.meepleverse.payments;

import io.fluxzero.sdk.tracking.RetryingErrorHandler;

import java.time.Duration;

public class PaymentRetries extends RetryingErrorHandler {
    public PaymentRetries() {
        super(1, Duration.ofMillis(250), PaymentRetries::isTechnicalError, false, true);
    }
}
