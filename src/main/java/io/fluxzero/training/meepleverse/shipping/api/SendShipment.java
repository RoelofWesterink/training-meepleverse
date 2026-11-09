package io.fluxzero.training.meepleverse.shipping.api;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.exception.FunctionalException;
import io.fluxzero.sdk.tracking.TrackSelf;
import io.fluxzero.sdk.web.HttpRequestMethod;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.training.meepleverse.authentication.Sender;
import io.fluxzero.training.meepleverse.common.SendWebRequest;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentDetails;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

@Value
@TrackSelf
public class SendShipment extends SendWebRequest {
    String proxyConsumer = "proxy-shipments";

    @NotNull ShipmentId shipmentId;
    @Valid @NotNull ShipmentDetails details;

    @Override
    public Object handle(Sender sender) {
        Fluxzero.loadAggregate(shipmentId).assertAndApply(new ShipmentSent(shipmentId, details));
        try {
            return super.handle(sender);
        } catch (FunctionalException e) {
            Fluxzero.publishEvent(new ShippingFailed(shipmentId, details.orderId()));
            throw e;
        }
    }

    @Override
    protected WebRequest.Builder buildRequest(WebRequest.Builder requestBuilder, Sender sender) {
        return requestBuilder
                .url("%s/send".formatted(requireProperty("shipper.url")))
                .payload(this)
                .method(HttpRequestMethod.POST);
    }
}
