package io.fluxzero.training.meepleverse.supplier;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.exception.FunctionalException;
import io.fluxzero.sdk.tracking.TrackSelf;
import io.fluxzero.sdk.web.HttpRequestMethod;
import io.fluxzero.sdk.web.WebRequest;
import io.fluxzero.training.meepleverse.authentication.Sender;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.common.SendWebRequest;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import lombok.Value;

import java.util.List;

import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

@Value
@TrackSelf
public class BackorderItems extends SendWebRequest {
    String proxyConsumer = "proxy-backorders";

    List<ItemId> items;
    OrderId orderId;
    String reference;

    @Override
    public Object handle(Sender sender) {
        try {
            return super.handle(sender);
        } catch (FunctionalException e) {
            Fluxzero.publishEvent(new BackorderFailed(orderId));
            throw e;
        }
    }

    @Override
    protected WebRequest.Builder buildRequest(WebRequest.Builder requestBuilder, Sender sender) {
        return requestBuilder
                .url("%s/order".formatted(requireProperty("supplier.url")))
                .payload(this)
                .method(HttpRequestMethod.POST);
    }
}
