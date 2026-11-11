package io.fluxzero.training.meepleverse.orders.view;

import io.fluxzero.sdk.persisting.search.Searchable;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;

import java.time.Instant;
import java.util.List;

@Searchable(collection = "shop-order-observations", timestampPath = "at")
public record OrderObservation(OrderId orderId, Kind kind, List<ItemId> itemIds,
                               ShipmentId shipmentId, String reason, Instant at) {
    public enum Kind {
        ORDER_RECEIVED, PAYMENT_ACCEPTED, PAYMENT_REJECTED, ITEM_ARRIVED,
        BACKORDER_FAILED, SHIPMENT_RECORDED, SHIPMENT_CONFIRMED, SHIPPING_FAILED, ORDER_ABORTED
    }
}
