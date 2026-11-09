package io.fluxzero.training.meepleverse.payments.api;

import io.fluxzero.sdk.persisting.eventsourcing.Apply;
import io.fluxzero.training.meepleverse.payments.api.model.Payment;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentAccepted(@NotNull PaymentId paymentId, @NotBlank String reference) {
    @Apply
    Payment apply() {
        return new Payment(paymentId, reference, true);
    }
}
