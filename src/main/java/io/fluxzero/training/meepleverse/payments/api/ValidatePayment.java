package io.fluxzero.training.meepleverse.payments.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxzero.common.MessageType;
import io.fluxzero.common.serialization.JsonUtils;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.logging.ConsoleError;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.TrackSelf;
import io.fluxzero.sdk.tracking.handling.HandleError;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.sdk.tracking.handling.Trigger;
import io.fluxzero.sdk.web.HttpRequestMethod;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.sdk.web.WebResponse;
import io.fluxzero.training.meepleverse.authentication.Sender;
import io.fluxzero.training.meepleverse.common.SendWebRequest;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

import static io.fluxzero.sdk.configuration.ApplicationProperties.getProperty;
import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

@Value
@TrackSelf
@Consumer(name = "payments")
public class ValidatePayment extends SendWebRequest implements Request<Boolean> {

    @HandleError
    static void retry(ConsoleError error, @Trigger(messageType = MessageType.COMMAND) ValidatePayment failed) {
        Fluxzero.sendCommand(failed);
    }
    String proxyConsumer = "proxy-payments";

    @NotNull
    PaymentId paymentId;
    @NotNull String reference;

    @Override
    public Boolean handle(Sender sender) {
        boolean accepted = (Boolean) super.handle(sender);
        Fluxzero.loadAggregate(paymentId).apply(
                accepted ? new PaymentAccepted(paymentId, reference) : new PaymentRejected(paymentId, reference));
        return accepted;
    }

    @Override
    protected WebRequest.Builder buildRequest(WebRequest.Builder requestBuilder, Sender sender) {
        return requestBuilder
                .url("%s/validate/%s".formatted(requireProperty("psp.url"), paymentId))
                .method(HttpRequestMethod.POST)
                .header("Authorization", "Bearer %s".formatted(getProperty("psp.token")));
    }

    @Override
    protected Boolean handleResponse(WebResponse response, WebRequest request) {
        String stringResult = (String) super.handleResponse(response, request);
        JsonNode result = JsonUtils.fromJson(stringResult, JsonNode.class);
        return result.get("accepted").asBoolean();
    }
}
