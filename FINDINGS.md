# Meepleverse — Codebase Findings

## Quick reference

| Category | IDs | Status |
|---|---|---|
| Bugs / flaws | B1–B5 | open |
| Performance | P1, P3–P7 | P1 fixed via consumer split |
| Race / consumer issues | R1–R7 | R4 mitigated via consumer split |
| Likely exercises | Ex 6 confirmed; Ex B2, R1, R2, R5, R6, R7, P3 probable | see [Exercises](#exercises) |

Jump to: [Exercises](#exercises) · [Stack](#stack) · [Architecture](#architecture) · [Aggregates](#aggregates) · [Commands](#commands) · [Events](#events) · [Event handlers](#event-handlers) · [Queries](#queries) · [Web endpoints](#web-endpoints) · [Auth](#authentication--authorisation) · [Projections](#projections--search) · [Order flow](#order-flow) · [Issues](#issues) · [Fixes applied](#fixes-applied)

---

## Fixes applied

### Consumer relocation (Exercise 1 solution)

**Previous approach (removed):** `@Consumer` on event handlers (`OrderFulfillment`, `OrderProgressProjection`, `CustomerNotifier`).

**New approach:** `@Consumer` on external HTTP command handlers:

| Consumer | Command | Rationale |
|---|---|---|
| `payments` | `ValidatePayment` | PSP HTTP calls; enables replay of failed payments via `minIndex` |
| `backorders` | `BackorderItems` | Supplier HTTP calls; enables replay of failed backorders |
| `shipments` | `SendShipment` | Shipper HTTP calls; enables replay of failed shipments |

**Why this is better:**
- The blocking HTTP calls are the actual bottlenecks, not the event handlers
- Named consumers on commands enable **replay via `minIndex`** to recover failed external calls
- Event handlers share the default consumer — they're fast and don't need isolation
- Supports the "payment mayhem" recovery scenario: change `payments` consumer's `minIndex` to reprocess past `ValidatePayment` commands

---

## Exercises

Likely exercise targets, ranked by training value. Each entry links to the relevant issue ID(s), names the files to touch, and states the concept being taught.

### Ex 6 · Index the game categories (confirmed, `exercises/06-catalog-facets.md`)

**Issue:** P3  
**Problem:** `SearchItems` fetches all `Item` aggregates, counts categories in Java, and filters in-memory. Slow and unscalable.  
**Fix:** Replace with `facetStats()` for counts and `matchFacet(...)` for filtering. Add a `@HandleDocument` consumer to reindex existing `Item` documents after the `@Facet` annotation is added.  
**Files:** `SearchItems.java`, `ItemDetails.java`, `Item.java`, `CatalogTest.java`  
**Teaches:** Native index facets, `@Facet`, `@HandleDocument` for document migration, why existing documents need reindexing after a model change.

---

### Ex ? · Fix the dead `Order.paid` field

**Issue:** B2  
**Problem:** `Order.paid` is never set to `true`. `PaymentAccepted` creates a `Payment` aggregate but never updates the `Order`. Any code reading `order.paid()` silently gets `false`.  
**Fix:** Add an `@Apply` method on `Order` for `PaymentAccepted` that sets `paid = true`, or update the existing apply path so the field reflects reality.  
**Files:** `Order.java`, `PaymentAccepted.java`, `OrderFulfillmentTest.java`  
**Teaches:** Event sourcing consistency — aggregate state must reflect all relevant events; the danger of dead fields.

---

### Ex ? · Guard against shipping an aborted order (race condition)

**Issue:** R1  
**Problem:** For multi-item orders, a late `BackorderedItemArrived` event can trigger `SendShipment` after the order has already been aborted. `OrderFulfillment.handle(BackorderedItemArrived)` has no `order.aborted()` check.  
**Fix:** Add `if (order.aborted()) return;` before `sendAndForgetCommand(SendShipment)`.  
**Files:** `OrderFulfillment.java`, `OrderFulfillmentTest.java`  
**Teaches:** Defensive event handler design; why aggregate state must be checked before acting on late-arriving events; the README's two-tab checkout exercise demonstrates this race live.

---

### Ex ? · Prevent duplicate AbortOrder commands

**Issue:** R2  
**Problem:** Three independent abort paths (`PaymentRejected`, `BackorderFailed`, `ShippingFailed`) can all fire `AbortOrder` for the same order concurrently. `IF_MODIFIED` on `Order` reduces but does not eliminate double dispatch.  
**Fix:** Add an `order.aborted()` guard in each abort-triggering handler before sending the command.  
**Files:** `OrderFulfillment.java`, `Order.java`  
**Teaches:** Idempotency in event-driven systems; `IF_MODIFIED` semantics and its limits; why fire-and-forget commands still need guards.

---

### Ex ? · Fix premature "processing" email

**Issue:** R5  
**Problem:** `CustomerNotifier` sends "We're processing your order" on every `PlaceOrder` event — including orders that are rejected within milliseconds. The customer then immediately receives an "aborted" email too.  
**Fix:** Move the "processing" notification to `PaymentAccepted`, so it only fires when payment is confirmed.  
**Files:** `CustomerNotifier.java`  
**Teaches:** Consumer independence and ordering; UX consequences of reacting too early in an async pipeline.

---

### Ex ? · Add `@TrackSelf` to failure events

**Issue:** R6  
**Problem:** `BackorderFailed`, `ShippingFailed`, and `BackorderedItemArrived` are invisible in the Devboard audit log — no `@TrackSelf`.  
**Fix:** Add `@TrackSelf` to each of these event records.  
**Files:** `BackorderFailed.java`, `ShippingFailed.java`, `BackorderedItemArrived.java`  
**Teaches:** Observability as a first-class concern; how `@TrackSelf` works; why failure events are the most important ones to trace.

---

### Ex ? · Secure the supplier webhook

**Issue:** R7  
**Problem:** `POST /supply/arrival/{orderId}/{itemId}` has no authentication. Any caller can trigger a `BackorderedItemArrived` event and advance an order through the pipeline.  
**Fix:** Add a shared-secret header check or require a system-level token on `SupplyEndpoint.registerArrival()`.  
**Files:** `SupplyEndpoint.java`  
**Teaches:** Securing internal webhooks; trust boundaries between internal services; the difference between public and internal API surfaces.

---

### Ex ? · Make `OrderProgressProjection` writes async

**Issue:** P4  
**Problem:** Every event in the order pipeline blocks on `indexAndWait()` in `OrderProgressProjection`. This adds synchronous latency to every step.  
**Fix:** Switch to async indexing. Accept that the shop UI may briefly show stale data (the `projectionPending` flag already handles this).  
**Files:** `OrderProgressProjection.java`  
**Teaches:** Synchronous vs async projections; the consistency/latency trade-off; when `projectionPending` is the right UX pattern.

---

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

## Architecture

### Message flow

```
Web request
  → @HandlePost / @HandleGet  (endpoint)
    → sendCommand / sendAndForgetCommand
      → @HandleCommand  (mutates aggregate → persists event)
        → @HandleEvent listeners (each in its own consumer):
            OrderFulfillment        [order-fulfillment]    → sends next command
            OrderProgressProjection [order-projection]     → writes read-model
            CustomerNotifier        [customer-notifications] → sends notification
```

### Consumer isolation

Each event handler class runs in its own dedicated consumer (via `@Consumer` annotation). This prevents slow handlers from blocking others when processing event batches.

### `@TrackSelf` — audit logging

Records every instance of that message in the Devboard audit log. Pure cross-cutting concern, no routing effect.

Applied to: `OrderUpdate`, `ShipmentUpdate`, `UserCommand`, `ValidatePayment`, `BackorderItems`, `SendShipment`.

NOT applied to: `BackorderFailed`, `ShippingFailed`, `BackorderedItemArrived` — harder to trace in Devboard (see R6).

### `@HandleCommand` — mutates state

Two patterns in this codebase:

| Pattern | Used by | Behaviour |
|---|---|---|
| Interface default | `OrderUpdate`, `ShipmentUpdate`, `UserCommand` | `loadAggregate().assertAndApply(this)` shared across all implementing commands |
| External call | `ValidatePayment`, `BackorderItems`, `SendShipment` | Extends `SendWebRequest`; makes a blocking HTTP call, then applies events |

### `@HandleEvent` — reacts to facts

Events are already-persisted facts. Multiple handlers receive the same event independently, each in their own consumer.

| Role | Class | Consumer | What it does |
|---|---|---|---|
| Orchestration | `OrderFulfillment` | `order-fulfillment` | Sends the next command in the pipeline |
| Projection | `OrderProgressProjection` | `order-projection` | Writes a read-model document |
| Side effects | `CustomerNotifier` | `customer-notifications` | Sends email notifications |

**Aggregate loading in handlers:**

`OrderFulfillment` uses explicit aggregate loading (not framework injection):
- `handle(PaymentAccepted)`: `Fluxzero.<Order>loadEntityValue(event.reference())`
- `handle(PaymentRejected)`: `Fluxzero.<Order>loadEntityValue(event.reference())`
- `handle(BackorderedItemArrived)`: `Fluxzero.loadAggregate(event.orderId()).get()`

`CustomerNotifier` uses framework aggregate injection:
- `handle(PlaceOrder event, Order order)` — framework injects matching aggregate
- `handle(AbortOrder event, Order order)` — framework injects matching aggregate
- `handle(ShipmentSent event, Shipment shipment)` — framework injects matching aggregate

`OrderProgressProjection` uses timestamp injection only:
- `on(PlaceOrder event, Instant timestamp)` — no aggregate needed

### `@HandleQuery` — reads state

Read-only. `SearchItems` and `GetUsers` define their handler inline on the record itself.

### Proxy consumers

Named `proxyConsumer` values (`proxy-payments`, `proxy-backorders`, `proxy-shipments`) are proxy routing channel names for external HTTP calls — not application handler identities.

---

## Aggregates

All use `eventPublication = IF_MODIFIED` — events only published when state actually changes.

| Aggregate | Id type | Notes |
|---|---|---|
| `Order` | `OrderId` | Holds `OrderDetails`, `paid` (dead — see B2), `aborted`, `customerId` |
| `Item` | `ItemId` | `timestampPath = "details.releaseDate"`; `details.category` is `@Facet` |
| `Payment` | `PaymentId` | `@EntityId` on `paymentId`; holds `reference` and `accepted` flag |
| `Shipment` | `ShipmentId` | Holds `ShipmentDetails` (itemId, orderId, addressee) |
| `UserProfile` | `UserId` | Holds `UserDetails` and `Role` |

Key patterns:
- `Order` uses `@With` (Lombok) for modified copies in `AbortOrder.apply(Order)`
- `UserProfile` uses `@Builder(toBuilder = true)` in `SetRole.apply(UserProfile)`
- `Item.details.category` is `@Facet` — index supports native facet stats (not yet used — see P3)

---

## Commands

### Command interfaces (shared behaviour)

| Interface | `@HandleCommand` default | `@TrackSelf` | Implementors |
|---|---|---|---|
| `OrderUpdate` | `loadAggregate(orderId()).assertAndApply(this)` | yes | `PlaceOrder`, `AbortOrder` |
| `ShipmentUpdate` | `loadAggregate(shipmentId()).assertAndApply(this)` | yes | `ShipmentSent` |
| `UserCommand` | `loadAggregate(userId()).assertAndApply(this)` | yes | `CreateUser`, `SetRole` |

### Concrete commands

| Command | Auth | Notes |
|---|---|---|
| `PlaceOrder` | `@NoUserRequired` | Carries `customerId` for member orders |
| `AbortOrder` | `@RequiresRole(ADMIN)` | Only ever sent by `OrderFulfillment` as system user (see B4) |
| `RegisterItem` | — | Dispatched in bulk by `RegisterItems` at startup |
| `RegisterItems` | — | Handled by `CatalogHandler`; iterates and dispatches `RegisterItem` |
| `ValidatePayment` | — | Extends `SendWebRequest`; HTTP POST to PSP; applies `PaymentAccepted`/`PaymentRejected` via `loadAggregate().apply()` |
| `BackorderItems` | — | Extends `SendWebRequest`; HTTP POST to supplier |
| `SendShipment` | — | Extends `SendWebRequest`; applies `ShipmentSent` before HTTP call (see B5, R3) |
| `CreateUser` | `@RequiresRole(ADMIN)` | `@AssertLegal` rejects if user already exists |
| `SetRole` | `@RequiresRole(ADMIN)` | `@AssertLegal` rejects if user not found |
| `SendEmail` | — | Fire-and-forget; no handler in main app |

### `SendWebRequest` error mapping

| HTTP status | Exception thrown |
|---|---|
| 400 | `ValidationException` |
| 401 | `UnauthorizedException` |
| 403 / 429 | `IllegalCommandException` |
| 404 | `null` |
| 5xx | `IllegalStateException` |

---

## Events

| Event | `@Apply` target | Published by |
|---|---|---|
| `PlaceOrder` | creates `Order` | `OrdersEndpoint`, `MemberCheckout` |
| `AbortOrder` | sets `Order.aborted = true` | `OrderFulfillment` |
| `PaymentAccepted` | creates `Payment(accepted=true)` | `ValidatePayment.handle()` via `loadAggregate().apply()` |
| `PaymentRejected` | creates `Payment(accepted=false)` | `ValidatePayment.handle()` via `loadAggregate().apply()` |
| `ShipmentSent` | creates `Shipment` | `SendShipment.handle()` (before HTTP call — see B5) |
| `ShippingFailed` | none | `SendShipment.handle()` on `FunctionalException` |
| `BackorderedItemArrived` | none | `SupplyEndpoint.registerArrival()` webhook |
| `BackorderFailed` | none | `BackorderItems.handle()` on `FunctionalException` |
| `RegisterItem` | creates `Item` | `CatalogHandler` |

`ShippingFailed`, `BackorderedItemArrived`, and `BackorderFailed` have no aggregate — they are pure domain events.

**Important:** `PaymentAccepted`/`PaymentRejected` must be applied via `loadAggregate().apply()` to create the `Payment` aggregate. Using `publishEvent()` directly would skip aggregate creation and break the payment flow.

---

## Event handlers

### `OrderFulfillment` — orchestrates the order pipeline

Consumer: `order-fulfillment`

| Trigger | Action |
|---|---|
| `PlaceOrder` | `sendAndForgetCommand(ValidatePayment)` |
| `PaymentAccepted` | loads Order via `loadEntityValue` → `sendAndForgetCommand(BackorderItems)` |
| `PaymentRejected` | loads Order via `loadEntityValue` → `sendAndForgetCommand(AbortOrder)` |
| `BackorderedItemArrived` | loads Order via `loadAggregate` → `sendAndForgetCommand(SendShipment)` — no abort guard (see R1) |
| `BackorderFailed` | `sendAndForgetCommand(AbortOrder)` |
| `ShippingFailed` | `sendAndForgetCommand(AbortOrder)` |

All commands are fire-and-forget. Multiple abort paths can race (see R2).

### `OrderProgressProjection` — builds the order timeline

Consumer: `order-projection`

Writes `OrderObservation` documents to `shop-order-observations` using `indexAndWait()` (synchronous — see P4). Key pattern: `orderId:kind:subject` for idempotent reprocessing.

| Trigger | Kind stored |
|---|---|
| `PlaceOrder` | `ORDER_RECEIVED` |
| `PaymentAccepted` | `PAYMENT_ACCEPTED` |
| `PaymentRejected` | `PAYMENT_REJECTED` |
| `BackorderedItemArrived` | `ITEM_ARRIVED` |
| `BackorderFailed` | `BACKORDER_FAILED` |
| `ShipmentSent` | `SHIPMENT_RECORDED` |
| `ShippingFailed` | `SHIPPING_FAILED` |
| `AbortOrder` | `ORDER_ABORTED` |

Runs independently of `OrderFulfillment` in its own consumer. The `projectionPending` flag in the shop UI surfaces any lag (see R4).

### `CustomerNotifier` — sends emails on key events

Consumer: `customer-notifications`

Uses framework aggregate injection to avoid explicit `loadAggregate`.

| Trigger | Email sent |
|---|---|
| `PlaceOrder` | "We're processing your order" — sent even if order is immediately rejected (see R5) |
| `ShipmentSent` | "We've sent an item" |
| `AbortOrder` | "Your order was aborted" |

---

## Queries

| Query | Handler | Returns |
|---|---|---|
| `SearchItems(term, category)` | inline `@HandleQuery` on the record | `Result(List<Item>, Map<String,Integer> categories)` — in-memory category counting (see P3) |
| `GetUsers()` | inline `@HandleQuery` on the record | `List<UserProfile>` via `Fluxzero.search(UserProfile.class).fetchAll()` |

---

## Web endpoints

| Class | Path | Auth |
|---|---|---|
| `OrdersEndpoint` | `POST /orders`, `GET /orders/{orderId}` | `@NoUserRequired`; anonymous orders only |
| `CatalogEndpoint` | `GET /catalog/browse`, `GET /catalog` | `@NoUserRequired` |
| `UsersEndpoint` | `POST /users`, `GET /users` | ADMIN required for create |
| `SupplyEndpoint` | `POST /supply/arrival/{orderId}/{itemId}` | none (see R7) |
| `ShopEndpoint` | `GET /` → redirect to `/shop/` | `@NoUserRequired`; serves static files |
| `ShopLogin` | `/shop-account/login`, `/app/callback`, `/shop-account/logout` | `@NoUserRequired`; OIDC + PKCE |
| `MemberCheckout` | `GET /shop-account/profile`, `POST /shop-account/delivery`, `POST /shop-account/orders`, `GET /shop-account/orders`, `GET /shop-account/orders/{orderId}` | Cookie session + CSRF header |

---

## Authentication & authorisation

- `SenderProvider` extends `AbstractUserProvider`; resolves `Sender` from messages
- **All web requests run as `Sender.system` (ADMIN)** — intentional demo shortcut (see B1)
- Role hierarchy: `MANAGER` ← `ADMIN` ← `OWNER`
- `@RequiresRole` is a meta-annotation wrapping `@RequiresAnyRole`
- `UserCommand.assertAuthorized` allows admins or the user themselves to act on a profile
- Shop auth: OIDC via Dev Server managed IDP; sessions stored as JSON files on disk (30-day lifetime); CSRF token checked on mutating requests

---

## Projections & search

| Collection | Document type | Written by | Key pattern |
|---|---|---|---|
| `shop-order-observations` | `OrderObservation` | `OrderProgressProjection` | `orderId:kind:subject` |
| *(aggregate store)* | `Item` | `CatalogHandler` | `itemId` |
| *(aggregate store)* | `Order` | `OrderUpdate.handle()` | `orderId` |
| *(aggregate store)* | `Payment` | `ValidatePayment` | `paymentId` |
| *(aggregate store)* | `Shipment` | `SendShipment` | `shipmentId` |
| *(aggregate store)* | `UserProfile` | `UserCommand.handle()` | `userId` |

`OrderObservation` is `@Searchable` (not `@Aggregate`) — read-model document, not event-sourced.

---

## Order flow

```
POST /shop-account/orders  (MemberCheckout)
  sendCommand(PlaceOrder)
    → PlaceOrder event → Order created
      OrderFulfillment [order-fulfillment]: sendAndForgetCommand(ValidatePayment)
        ValidatePayment: HTTP POST /psp/validate/{paymentId}
          → loadAggregate(paymentId).apply(PaymentAccepted/PaymentRejected) → Payment created
            OrderFulfillment (PaymentAccepted):
              loadEntityValue(reference) → sendAndForgetCommand(BackorderItems)
                BackorderItems: HTTP POST /supplier/order
                  → BackorderedItemArrived (per item, via SupplyEndpoint webhook)
                    OrderFulfillment (BackorderedItemArrived):
                      loadAggregate(orderId) → sendAndForgetCommand(SendShipment)
                        SendShipment: apply ShipmentSent → HTTP POST /shipper/send
                          → (success) done
                          → (failure) ShippingFailed event
                            OrderFulfillment: sendAndForgetCommand(AbortOrder)
                  → (failure) BackorderFailed event
                    OrderFulfillment: sendAndForgetCommand(AbortOrder)
            OrderFulfillment (PaymentRejected):
              loadEntityValue(reference) → sendAndForgetCommand(AbortOrder)

Each event also triggers (in parallel, separate consumers):
  OrderProgressProjection [order-projection]     → indexAndWait() to shop-order-observations
  CustomerNotifier        [customer-notifications] → sendAndForgetCommand(SendEmail)
```

---

## Issues

### Bugs / flaws

| # | Severity | Location | Issue |
|---|---|---|---|
| B1 | Medium | `SenderProvider` | `@RequiresRole(MANAGER)` on the package is silently bypassed — all web requests run as `Sender.system` (ADMIN). Guards are ineffective and misleading for HTTP callers. |
| B2 | Medium | `Order` | `paid` field is never set to `true`. `PaymentAccepted` creates a `Payment` aggregate but never updates `Order.paid`. The field is dead; any code reading `order.paid()` always returns `false`. |
| B3 | Low | `UserTest` | `createViewer()` and `createUser()` are byte-for-byte identical — copy-paste leftover. |
| B4 | Low | `AbortOrder` | Annotated `@RequiresRole(ADMIN)` but only ever sent by `OrderFulfillment` as the system user. No distinction between a system-triggered abort and an admin manually aborting an order. |
| B5 | Low | `SendShipment` | `ShipmentSent` is applied before the HTTP call to the shipper. If the command rolls back, the event may not persist — leaving `ShippingFailed` visible without a matching `SHIPMENT_RECORDED`. The shop UI handles this but the ordering is fragile. |

### Performance issues

| # | Impact | Location | Status | Issue |
|---|---|---|---|---|
| P1 | High | Event handlers | ✅ Fixed | Handlers sharing a single consumer blocked each other. **Fixed via `@Consumer` split** — each handler now runs in its own consumer (`order-fulfillment`, `order-projection`, `customer-notifications`). |
| P3 | High | `SearchItems` | open | Fetches all items, counts categories in Java, filters in-memory. `ItemDetails.category` is `@Facet` — replace with `facetStats()` and `matchFacet(...)`. Exercise 6 target. |
| P4 | Medium | `OrderProgressProjection` | open | All observation writes use `indexAndWait()` — synchronous, adds latency. Consumer split prevents this from blocking fulfillment. Could switch to async indexing for further improvement. |
| P5 | Medium | `ShopSessions.create()` | open | Scans the entire session directory on every login to clean up expired files — O(n) filesystem scan. Use a background task or size-bounded cache instead. |
| P6 | Low | `MemberCheckout.buy()` | open | `profileFor(member)` hits the key-value store on every order. Could be cached in-memory (Caffeine is already on the classpath). |
| P7 | Low | `OrderStatus.from()` | open | Each status request does `search(OrderObservation.class)` + `loadAggregate(paymentId)` — two blocking calls. Acceptable for single-order views; would not scale for bulk listing. |

### Race conditions & consumer issues

| # | Severity | Location | Issue |
|---|---|---|---|
| R1 | High | `OrderFulfillment.handle(BackorderedItemArrived)` | Multi-item orders: a second `BackorderedItemArrived` can trigger `SendShipment` after the order has already been aborted. No `order.aborted()` guard before sending `SendShipment`. |
| R2 | Medium | `OrderFulfillment` | Multiple abort paths (`PaymentRejected`, `BackorderFailed`, `ShippingFailed`) can all send `AbortOrder` for the same order concurrently. `IF_MODIFIED` on `Order` mitigates but does not prevent double command execution. |
| R3 | Medium | `SendShipment` | `SendShipment` is `@TrackSelf` directly AND implements `ShipmentUpdate` which is also `@TrackSelf` — tracked twice. Also has a `@HandleCommand` conflict: both `SendWebRequest.handle()` and `ShipmentUpdate.handle()` default are present. Ambiguous which fires. |
| R4 | Low | `OrderProgressProjection` vs `OrderFulfillment` | Independent consumers — projection can lag behind fulfillment. **Mitigated by consumer split** — they no longer block each other. The `projectionPending` flag in the shop UI surfaces any remaining lag. |
| R5 | Low | `CustomerNotifier.on(PlaceOrder)` | Sends "We're processing your order" email immediately on `PlaceOrder`, even when the order is about to be rejected. Customer receives both a "processing" and an "aborted" email in quick succession. |
| R6 | Low | `BackorderFailed`, `ShippingFailed`, `BackorderedItemArrived` | None of these events are `@TrackSelf`. They do not appear in the Devboard audit log, making failures harder to trace without additional tooling. |
| R7 | Medium | `SupplyEndpoint.registerArrival()` | `POST /supply/arrival/{orderId}/{itemId}` has no authentication. Any caller can trigger a `BackorderedItemArrived` event for any order, potentially advancing an order through the pipeline without a real supplier delivery. |

---

## Test coverage

| Test class | Covers |
|---|---|
| `OrderFulfillmentTest` | Full order lifecycle, payment rejection, supplier failure, shipper failure; uses `ExternalsMock` with `@HandlePost` handlers for `/psp/validate/{paymentId}`, `/supplier/order`, `/shipper/send` |
| `ShopOrderTest` | Shop-level order placement |
| `MemberCheckoutTest` | Member buy flow, validation |
| `CatalogTest` | Item registration and search |
| `ShopSessionsTest` | Session create / find / remove / expiry |
| `UserTest` | User creation and roles (note B3: duplicate helper method) |

Tests use `TestFixture` with in-memory fixtures and mocked external services — no Docker required.

---

## Anti-patterns to avoid

Based on issues encountered during development:

1. **Don't use `publishEvent()` for events with `@Apply` methods** — Use `loadAggregate().apply()` instead. `publishEvent()` skips aggregate creation/mutation.

2. **Don't assume framework aggregate injection works everywhere** — It requires:
   - The event to have a typed ID field (e.g., `OrderId`), not a String
   - The event to be linked to an aggregate via `@Apply` or similar mechanism
   - `PaymentAccepted`/`PaymentRejected` have `reference` (String), so injection fails
   - `BackorderedItemArrived` has `OrderId` but no `@Apply`, so injection also fails in test fixtures
   - The explicit `loadEntityValue`/`loadAggregate` calls in `OrderFulfillment` are **required**, not sub-optimal

3. **Don't remove validation checks without understanding their purpose** — The `MemberCheckout.buy()` item existence check (`loadAggregate(id).get() == null`) prevents orders for non-existent items.
