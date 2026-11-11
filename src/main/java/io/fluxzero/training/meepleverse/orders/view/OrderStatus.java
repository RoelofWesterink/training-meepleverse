package io.fluxzero.training.meepleverse.orders.view;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.Order;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.model.ShipmentId;

import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

import static io.fluxzero.training.meepleverse.orders.view.OrderObservation.Kind.*;

public record OrderStatus(OrderId orderId, List<ItemId> itemIds, String paymentStatus, boolean aborted,
                          String abortReason, boolean projectionPending, List<OrderObservation> observations,
                          List<ShipmentView> shipments) {
    public static OrderStatus from(Order order) {
        List<OrderObservation> observations = Fluxzero.search(OrderObservation.class)
                .match(order.orderId(), "orderId").fetchAll();
        observations = observations.stream().sorted(Comparator.comparing(OrderObservation::at)).toList();
        var payment = order.details().paymentId() == null ? null
                : Fluxzero.loadAggregate(order.details().paymentId()).get();
        var reason = observations.stream().filter(o -> o.kind() == ORDER_ABORTED)
                .map(OrderObservation::reason).reduce((first, last) -> last).orElse(null);
        var failedShipments = observations.stream().filter(o -> o.kind() == SHIPPING_FAILED)
                .map(OrderObservation::shipmentId).toList();
        var confirmedShipments = observations.stream().filter(o -> o.kind() == SHIPMENT_CONFIRMED)
                .map(OrderObservation::shipmentId).toList();
        var shipments = new ArrayList<>(observations.stream().filter(o -> o.kind() == SHIPMENT_RECORDED)
                .map(o -> new ShipmentView(o.shipmentId(), o.itemIds(), o.at(),
                                          failedShipments.contains(o.shipmentId()) ? "FAILED" :
                                          confirmedShipments.contains(o.shipmentId()) ? "SENT" : "RECORDED"))
                .toList());
        // A failed command can roll back ShipmentSent. Keep the failure visible even without shipment details.
        observations.stream().filter(o -> o.kind() == SHIPPING_FAILED)
                .filter(o -> shipments.stream().noneMatch(s -> s.shipmentId().equals(o.shipmentId())))
                .forEach(o -> shipments.add(new ShipmentView(o.shipmentId(), List.of(), o.at(), "FAILED")));
        return new OrderStatus(order.orderId(), order.details().itemIds(),
                payment == null ? "PENDING" : payment.accepted() ? "ACCEPTED" : "REJECTED",
                order.aborted(), reason, observations.stream().noneMatch(o -> o.kind() == ORDER_RECEIVED),
                observations, shipments);
    }

    public record ShipmentView(ShipmentId shipmentId, List<ItemId> itemIds, Instant recordedAt, String status) {}
}
