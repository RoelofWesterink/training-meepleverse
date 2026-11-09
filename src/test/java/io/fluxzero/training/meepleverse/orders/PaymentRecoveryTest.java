package io.fluxzero.training.meepleverse.orders;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.web.HandlePost;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.ValidatePayment;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentRecoveryTest {
    final ValidatePayment command = new ValidatePayment(new PaymentId("payment1"), "order1");
    final AtomicInteger attempts = new AtomicInteger();

    TestFixture withPspFailing(int failures) {
        class Psp {
            @HandlePost("/psp/validate/{paymentId}")
            WebResponse validate(String paymentId) {
                if (attempts.incrementAndGet() <= failures) {
                    return WebResponse.builder().status(503).payload("PSP temporarily unavailable").build();
                }
                return WebResponse.builder().payload("{\"accepted\":true}").build();
            }
        }
        return TestFixture.createAsync(new Psp());
    }

    @Test
    void aTechnicalFailureGetsOneMoreChance() {
        withPspFailing(1).whenCommand(command).expectEvents(PaymentAccepted.class);
        assertEquals(2, attempts.get());
    }

    @Test
    void replaySkipsAnAlreadyCompletedPayment() {
        withPspFailing(0).givenCommands(command)
                .whenCommand(command)
                .expectResult(true).expectNoEventsLike(PaymentAccepted.class);
        assertEquals(1, attempts.get());
    }

    @Test
    void aPersistentFailureStopsAfterThreeAttempts() {
        withPspFailing(Integer.MAX_VALUE).whenCommand(command)
                .expectThat(f -> assertNull(Fluxzero.loadEntityValue(command.getPaymentId())));
        assertEquals(3, attempts.get());
    }
}
