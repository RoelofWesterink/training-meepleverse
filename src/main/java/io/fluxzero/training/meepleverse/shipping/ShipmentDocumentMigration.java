package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.training.meepleverse.shipping.api.model.Shipment;
import org.springframework.stereotype.Component;

/**
 * One-time migration: reindexes old Shipment documents with the new schema.
 * 
 * The handler receives documents upcast to revision 1 (itemIds list).
 * Returning the document persists the upcast version.
 * 
 * Remove this class after the migration completes.
 */
@Consumer(name = "shipment-schema-migration", minIndex = 0)
@Component
public class ShipmentDocumentMigration {

    @HandleDocument
    Shipment migrate(Shipment shipment) {
        return shipment;
    }
}
