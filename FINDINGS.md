# Meepleverse — Codebase Findings

## Stack

| Concern | Detail |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.2 |
| Event framework | Fluxzero SDK 1.292.49 |
| Build | Maven 3.10.0 via wrapper |
| Dev runtime | Fluxzero Dev Server 1.20.10 (`fz dev`) |
| External services | PSP, supplier, shipper — bundled JARs in `local/` |
| IDP | Managed by Dev Server (`idp: managed` in `dev.yaml`) |
| Session store | Local filesystem (`~/.fluxzero/shop-sessions/`) |

---

## Aggregates

Aggregates are event-sourced records annotated `@Aggregate`. All use `eventPublication = IF_MODIFIED` — events are only published when the aggregate state actually changes.

| Aggregate | Id type | Searchable | Notes |
|---|---|---|---|
| `Order` | `OrderId` | yes | Holds `OrderDetails`, `paid`, `aborted`, `customerId` |
| `Item` | `ItemId` | yes | `timestampPath = "details.releaseDate"` — sorted by release date |
| `Payment` | `PaymentId` | yes | `@EntityId` on `paymentId`; holds `reference` and `accepted` flag |
| `Shipment` | `ShipmentId` | yes | Holds `ShipmentDetails` (itemId, orderId, addressee) |
| `UserProfile` | `UserId` | yes | Holds `UserDetails` and `Role` |

### Key aggregate patterns

- `Order` uses `@With` (Lombok) to produce modified copies in `AbortOrder.apply(Order)`
- `UserProfile` uses `@Builder(toBuilder = true)` for the same purpose in `SetRole.apply(UserProfile)`
- `Item.details.category` is annotated `@Facet` — the index supports native facet stats (not yet used in `SearchItems`)
- `Payment` uses `@EntityId` explicitly; other aggregates rely on the field name convention

---

## Commands

### Command interfaces (shared behaviour via default methods)

| Interface | `@HandleCommand` default | `@TrackSelf` | Notes |
|---|---|---|---|
| `OrderUpdate` | `Fluxzero.loadAggregate(orderId()).assertAndApply(this)` | yes | Implemented by `PlaceOrder`, `AbortOrder` |
| `ShipmentUpdate` | `Fluxzero.loadAggregate(shipmentId()).assertAndApply(this)` | yes | Implemented by `ShipmentSent` |
| `UserCommand` | `Fluxzero.loadAggregate(userId()).assertAndApply(this)` | yes | Implemented by `CreateUser`, `SetRole`; also has `@AssertLegal assertAuthorized` |

### Concrete commands

| Command | Auth | `@Apply` | Notes |
|---|---|---|---|
| `PlaceOrder` | `@NoUserRequired` | creates `Order` | Also carries `customerId` for member orders |
| `AbortOrder` | `@RequiresRole(ADMIN)` | sets `order.aborted = true` | Triggered by payment rejection, backorder failure, shipping failure |
| `RegisterItem` | — | creates `Item` | Dispatched in bulk via `RegisterItems` at startup |
| `RegisterItems` | — | — | Handled by `CatalogHandler`; iterates and dispatches `RegisterItem` |
| `ValidatePayment` | — | — | Extends `SendWebRequest`; HTTP POST to PSP; publishes `PaymentAccepted`/`PaymentRejected` |
| `BackorderItems` | — | — | Extends `SendWebRequest`; HTTP POST to supplier; publishes `BackorderFailed` on `FunctionalException` |
| `SendShipment` | — | — | Extends `SendWebRequest`; applies `ShipmentSent` before HTTP call; publishes `ShippingFailed` on failure |
| `CreateUser` | `@RequiresRole(ADMIN)` | creates `UserProfile` | `@AssertLegal` guard rejects if user already exists |
| `SetRole` | `@RequiresRole(ADMIN)` | updates `UserProfile.role` | `@AssertLegal` guard rejects if user not found |
| `SendEmail` | — | — | Fire-and-forget notification; no handler in main app (external) |

### External command pattern (`SendWebRequest`)

`ValidatePayment`, `BackorderItems`, and `SendShipment` all extend `SendWebRequest`, which provides:
- `@HandleCommand @HandleQuery` on `handle(Sender sender)`
- `Fluxzero.get().webRequestGateway().sendAndWait(...)` — **blocking** HTTP call
- Error mapping: 400 → `ValidationException`, 401 → `UnauthorizedException`, 403/429 → `IllegalCommandException`, 404 → null, 5xx → `IllegalStateException`
- Each uses a named `proxyConsumer` (`proxy-payments`, `proxy-backorders`, `proxy-shipments`) for routing through the Fluxzero proxy

---

## Events

Events carry no `@HandleCommand` — they are applied to aggregates via `@Apply` methods or published directly.

| Event | `@Apply` target | Published by |
|---|---|---|
| `PlaceOrder` | creates `Order` | `OrdersEndpoint`, `MemberCheckout` |
| `AbortOrder` | sets `Order.aborted = true` | `OrderFulfillment` (via `sendAndForgetCommand`) |
| `PaymentAccepted` | creates `Payment(accepted=true)` | `ValidatePayment.handle()` |
| `PaymentRejected` | creates `Payment(accepted=false)` | `ValidatePayment.handle()` |
| `ShipmentSent` | creates `Shipment` | `SendShipment.handle()` (before HTTP call) |
| `ShippingFailed` | — (no `@Apply`) | `SendShipment.handle()` on `FunctionalException` |
| `BackorderedItemArrived` | — (no `@Apply`) | `SupplyEndpoint.registerArrival()` webhook |
| `BackorderFailed` | — (no `@Apply`) | `BackorderItems.handle()` on `FunctionalException` |
| `RegisterItem` | creates `Item` | `CatalogHandler` |

Note: `ShippingFailed`, `BackorderedItemArrived`, and `BackorderFailed` have no aggregate of their own — they are pure domain events consumed by `OrderFulfillment` and `OrderProgressProjection`.

---

## Event handlers (`@HandleEvent`)

### `OrderFulfillment` — orchestrates the order pipeline

| Trigger event | Action |
|---|---|
| `PlaceOrder` | `sendAndForgetCommand(ValidatePayment)` |
| `PaymentAccepted` | loads `Order`, `sendAndForgetCommand(BackorderItems)` |
| `PaymentRejected` | loads `Order`, `sendAndForgetCommand(AbortOrder)` |
| `BackorderFailed` | `sendAndForgetCommand(AbortOrder)` |
| `BackorderedItemArrived` | loads `Order` aggregate, `sendAndForgetCommand(SendShipment)` |
| `ShippingFailed` | `sendAndForgetCommand(AbortOrder)` |

All commands are fire-and-forget (`sendAndForgetCommand`). The `PaymentAccepted` and `BackorderedItemArrived` handlers each do a `loadAggregate` to get the order details needed for the next command.

### `OrderProgressProjection` — builds the order timeline

Listens to all order lifecycle events and writes `OrderObservation` documents to the `shop-order-observations` collection using `indexAndWait()` (synchronous). Each observation is keyed `orderId:kind:subject` to allow idempotent reprocessing.

| Trigger event | `OrderObservation.Kind` stored |
|---|---|
| `PlaceOrder` | `ORDER_RECEIVED` |
| `PaymentAccepted` | `PAYMENT_ACCEPTED` |
| `PaymentRejected` | `PAYMENT_REJECTED` |
| `BackorderedItemArrived` | `ITEM_ARRIVED` |
| `BackorderFailed` | `BACKORDER_FAILED` |
| `ShipmentSent` | `SHIPMENT_RECORDED` |
| `ShippingFailed` | `SHIPPING_FAILED` |
| `AbortOrder` | `ORDER_ABORTED` |

### `CustomerNotifier` — sends emails on key order events

Uses Fluxzero aggregate injection (`PlaceOrder event, Order order`) to receive the aggregate state alongside the event — avoids explicit `loadAggregate` calls.

| Trigger event | Action |
|---|---|
| `PlaceOrder` | `sendAndForgetCommand(SendEmail)` — "We're processing your order" |
| `ShipmentSent` | `sendAndForgetCommand(SendEmail)` — "We've sent an item" |
| `AbortOrder` | `sendAndForgetCommand(SendEmail)` — "Your order was aborted" |

---

## Queries

| Query | Handler | Returns |
|---|---|---|
| `SearchItems(term, category)` | inline `@HandleQuery` on the record | `Result(List<Item>, Map<String,Integer> categories)` — in-memory category counting (see observations) |
| `GetUsers()` | inline `@HandleQuery` on the record | `List<UserProfile>` via `Fluxzero.search(UserProfile.class).fetchAll()` |

---

## Web endpoints

| Class | Path | Methods | Auth |
|---|---|---|---|
| `OrdersEndpoint` | `/orders` | `POST` place order, `GET /{orderId}` status | `@NoUserRequired` on `PlaceOrder`; anonymous orders only (no `customerId`) |
| `CatalogEndpoint` | `/catalog` | `GET /browse`, `GET` | `@NoUserRequired` |
| `UsersEndpoint` | `/users` | `POST`, `GET` | `ADMIN` required for create |
| `SupplyEndpoint` | `/supply` | `POST /arrival/{orderId}/{itemId}` | — |
| `ShopEndpoint` | `/` | `GET /` redirect to `/shop/` | `@NoUserRequired`; serves static files from `classpath:/shop` |
| `ShopLogin` | `/shop-account/login`, `/app/callback`, `/shop-account/logout` | `GET`, `GET`, `POST` | `@NoUserRequired`; OIDC + PKCE flow |
| `MemberCheckout` | `/shop-account` | `GET /profile`, `POST /delivery`, `POST /orders`, `GET /orders`, `GET /orders/{orderId}` | Cookie session + CSRF header |

---

## Authentication & authorisation

- `SenderProvider` extends `AbstractUserProvider` and resolves `Sender` from messages
- **All web requests are treated as `Sender.system` (ADMIN)** — intentional demo shortcut, noted with a comment in `SenderProvider`
- Role hierarchy: `MANAGER` ← `ADMIN` ← `OWNER` (each higher role assumes lower roles)
- `@RequiresRole` is a meta-annotation wrapping `@RequiresAnyRole`
- `UserCommand.assertAuthorized` allows admins or the user themselves to act on a profile
- Shop authentication is separate: OIDC via the Dev Server managed IDP, sessions stored as JSON files on disk (30-day lifetime), CSRF token checked on mutating requests

---

## Projections & search

| Collection | Document type | Written by | Key pattern |
|---|---|---|---|
| `shop-order-observations` | `OrderObservation` | `OrderProgressProjection` | `orderId:kind:subject` |
| *(aggregate store)* | `Item` | `CatalogHandler` via `RegisterItem` | `itemId` |
| *(aggregate store)* | `Order` | `OrderUpdate.handle()` | `orderId` |
| *(aggregate store)* | `Payment` | `ValidatePayment` | `paymentId` |
| *(aggregate store)* | `Shipment` | `SendShipment` | `shipmentId` |
| *(aggregate store)* | `UserProfile` | `UserCommand.handle()` | `userId` |

`OrderObservation` is `@Searchable` (not `@Aggregate`) — it is a read-model document, not event-sourced.

---

## Order flow (complete)

```
POST /shop-account/orders  (MemberCheckout)
  sendCommand(PlaceOrder)
    → PlaceOrder event → Order created
      OrderFulfillment: sendAndForgetCommand(ValidatePayment)
        ValidatePayment: HTTP POST /psp/validate/{paymentId}
          → PaymentAccepted / PaymentRejected event → Payment created
            OrderFulfillment (PaymentAccepted):
              loadAggregate(orderId) → sendAndForgetCommand(BackorderItems)
                BackorderItems: HTTP POST /supplier/order
                  → BackorderedItemArrived event (per item, via SupplyEndpoint webhook)
                    OrderFulfillment (BackorderedItemArrived):
                      loadAggregate(orderId) → sendAndForgetCommand(SendShipment)
                        SendShipment: apply ShipmentSent → HTTP POST /shipper/send
                          → (success) done
                          → (failure) ShippingFailed event
                            OrderFulfillment: sendAndForgetCommand(AbortOrder)
                  → (failure) BackorderFailed event
                    OrderFulfillment: sendAndForgetCommand(AbortOrder)
            OrderFulfillment (PaymentRejected):
              loadAggregate(orderId) → sendAndForgetCommand(AbortOrder)

Every step also triggers OrderProgressProjection (indexAndWait) and CustomerNotifier (sendAndForgetCommand SendEmail)
```

---

## Performance issues found & fixed

### 1. Sequential item validation — `MemberCheckout.buy()`
**Problem:** N blocking `loadAggregate` calls (one per cart item) before the order was placed, serializing under concurrent load.
**Fix:** Removed per-item aggregate load. Structural validation (null, empty, duplicates, size > 100) retained.

### 2. Redundant aggregate load in payment handling — `ValidatePayment.handle()`
**Problem:** After the PSP HTTP call, `loadAggregate(paymentId).apply(...)` added an extra blocking round-trip just to apply an event.
**Fix:** Replaced with `Fluxzero.publishEvent(...)`.

---

## Other observations

### `SearchItems` — in-memory category counting (Exercise 6 target)
Fetches all items, counts categories in Java, filters by category in-memory. `ItemDetails.category` is already `@Facet` — the index supports `facetStats()` and `matchFacet(...)`. Exercise 6 replaces this with native index facets and adds a `@HandleDocument` consumer to reindex existing documents.

### `OrderStatus.from()` — multiple blocking calls per status request
Each `GET /shop-account/orders/{orderId}` does a `search(OrderObservation.class)` + `loadAggregate(paymentId)`. Fine for single-order views; would not scale for bulk listing.

### `SendShipment` applies `ShipmentSent` before the HTTP call
This means a `Shipment` aggregate exists even if the carrier call fails. `ShippingFailed` is then published and `OrderFulfillment` aborts the order. The shop UI handles the case where `SHIPMENT_RECORDED` and `SHIPPING_FAILED` exist for the same shipment.

### `OrderProgressProjection` uses `indexAndWait`
All projection writes are synchronous, adding latency to every event in the pipeline. Consistent for the UI but a potential bottleneck under load.

### No POST idempotency on order placement
A failed `POST /orders` or `POST /shop-account/orders` may have already created an order. There is no idempotency key on `PlaceOrder`. The README explicitly notes this.

### `ShopSessions` is filesystem-based and single-instance
Sessions are JSON files under `.fluxzero/shop-sessions/`. Expired sessions are cleaned up lazily on next login. This does not survive a multi-instance deployment.

### `SenderProvider` grants all web requests ADMIN
Every HTTP request to the backend runs as `Sender.system` (ADMIN). Role-based guards on commands (`@RequiresRole`) are only meaningful for non-web message sources in this demo.

---

## Known issues & optimisations

### Bugs / flaws

| # | Severity | Location | Issue |
|---|---|---|---|
| B1 | Medium | `package-info.java` | `@RequiresRole(MANAGER)` on the entire package is silently bypassed — `SenderProvider` grants all web requests `Sender.system` (ADMIN), making the package-level guard ineffective and misleading |
| B2 | Medium | `Order` | `paid` field is never set to `true` anywhere in the codebase. `PaymentAccepted` creates a `Payment` aggregate but never updates `Order.paid`. The field is dead; any code reading `order.paid()` always gets `false` |
| B3 | Low | `UserTest` | `createViewer()` and `createUser()` are byte-for-byte identical — one is a copy-paste leftover |
| B4 | Low | `AbortOrder` | Annotated `@RequiresRole(ADMIN)` but only ever sent by `OrderFulfillment` as the system user. No distinction between a system-triggered abort and an admin manually aborting an order |
| B5 | Low | `SendShipment` | `ShipmentSent` is applied before the HTTP call to the shipper. If the command rolls back, the event may not persist — leaving `ShippingFailed` visible in the UI without a matching `SHIPMENT_RECORDED` entry. The shop UI handles this case but the ordering is fragile |

### Performance optimisations

| # | Impact | Location | Issue |
|---|---|---|---|
| P1 | High | `OrderFulfillment.handle(PaymentAccepted)` | ~~Loads full `Order` aggregate just to get `itemIds` and `addressee.fullName()`.~~ **Fixed:** replaced `loadEntityValue` with framework aggregate injection (`Order order` parameter). |
| P2 | High | `OrderFulfillment.handle(BackorderedItemArrived)` | ~~Loads full `Order` aggregate just to get `addressee`.~~ **Fixed:** replaced `loadAggregate` with framework aggregate injection (`Order order` parameter). Also fixed `PaymentRejected` handler which had the same pattern. |
| P3 | High | `SearchItems` | Fetches all items, counts categories in Java, filters by category in-memory. `ItemDetails.category` is `@Facet` — replace with `facetStats()` and `matchFacet(...)` (Exercise 6 target) |
| P4 | Medium | `OrderProgressProjection` | All observation writes use `indexAndWait()` — synchronous, adds latency to every event in the order pipeline. Switch to async indexing if eventual consistency in the UI is acceptable |
| P5 | Medium | `ShopSessions.create()` | Scans the entire session directory on every login to clean up expired files — O(n) filesystem scan. Use a background task or size-bounded cache instead |
| P6 | Low | `MemberCheckout.buy()` | `profileFor(member)` hits the key-value store on every order. The profile is already a dependency of `Caffeine` (already in the classpath) and could be cached in-memory |
| P7 | Low | `OrderStatus.from()` | Each status request does `search(OrderObservation.class)` + `loadAggregate(paymentId)` — two blocking calls. Acceptable for single-order views but would not scale for bulk listing |

---

## Test coverage

| Test class | Covers |
|---|---|
| `OrderFulfillmentTest` | Full order lifecycle, payment rejection, supplier failure, shipper failure; uses `ExternalsMock` with `@HandlePost` handlers |
| `ShopOrderTest` | Shop-level order placement |
| `MemberCheckoutTest` | Member buy flow, validation |
| `CatalogTest` | Item registration and search |
| `ShopSessionsTest` | Session create/find/remove/expiry |
| `UserTest` | User creation and roles |

Tests use `TestFixture` with in-memory fixtures and mocked external services — no Docker required. The `ExternalsMock` in `OrderFulfillmentTest` registers `@HandlePost` handlers for `/psp/validate/{paymentId}`, `/supplier/order`, and `/shipper/send`.
