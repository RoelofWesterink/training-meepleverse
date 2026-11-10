# Fluxzero SDK 1.x Quick Reference

This summary extracts SDK 1.x patterns from the developer guides for use in Meepleverse.
The full guides document SDK 2.x (`@Model`, `Graph<T>`); this project uses SDK 1.x (`@Aggregate`).

---

## Core Concepts

### Message Types

| Kind | Purpose | Send method |
|---|---|---|
| Command | Request a change | `sendCommand()`, `sendAndForgetCommand()`, `sendCommandAndWait()` |
| Query | Request information | `query()`, `queryAndWait()` |
| Event | Report something happened | `publishEvent()` |
| Schedule | Do work later | `schedule()`, `scheduleCommand()` |

### Handler Annotations

| Annotation | Reacts to |
|---|---|
| `@HandleCommand` | Commands needing explicit handling |
| `@HandleQuery` | Information requests |
| `@HandleEvent` | Published events |
| `@HandleGet`, `@HandlePost`, etc. | HTTP requests |
| `@HandleSchedule` | Scheduled messages when due |

---

## SDK 1.x Aggregates (This Project)

### Declaration

```java
@Aggregate(eventPublication = EventPublication.IF_MODIFIED)
public record Order(@EntityId OrderId orderId, OrderDetails details, boolean paid, boolean aborted) {}
```

- `@Aggregate` — marks event-sourced state (SDK 1.x)
- `@EntityId` — identifies the aggregate instance
- `eventPublication = IF_MODIFIED` — only publish events when state changes

### Loading Aggregates

```java
// Load aggregate for mutation
Entity<Order> entity = Fluxzero.loadAggregate(orderId);
Order order = entity.get();
entity.assertAndApply(command);

// Load just the value (read-only)
Order order = Fluxzero.<Order>loadEntityValue(orderId);
```

**Critical:** `loadEntityValue()` returns the value only. `loadAggregate()` returns an `Entity<T>` for mutations.

### Applying Events

```java
// CORRECT: Creates/mutates aggregate via @Apply method
Fluxzero.loadAggregate(paymentId).apply(new PaymentAccepted(...));

// WRONG: Skips aggregate creation, just publishes event
Fluxzero.publishEvent(new PaymentAccepted(...));
```

**Rule:** Use `loadAggregate().apply()` when the event has an `@Apply` method that creates or mutates an aggregate.

### Command Handling Pattern

```java
// Interface with shared handler (used in Meepleverse)
@TrackSelf
public interface OrderUpdate {
    OrderId orderId();
    
    @HandleCommand
    default void handle() {
        Fluxzero.loadAggregate(orderId()).assertAndApply(this);
    }
}

// Command implementing the interface
public record PlaceOrder(OrderId orderId, OrderDetails details) implements OrderUpdate {
    @Apply
    Order apply() {
        return new Order(orderId, details, false, false);
    }
}
```

---

## Consumers

### `@Consumer` Annotation

```java
@Consumer(name = "order-fulfillment")
public class OrderFulfillment {
    @HandleEvent
    void handle(PlaceOrder event) { ... }
}
```

Isolates handlers into separate tracking positions. Benefits:
- Slow handlers don't block others
- Independent replay/recovery
- Parallel processing of different concerns

### Consumer Settings

| Setting | Purpose |
|---|---|
| `name` | Unique consumer identifier |
| `threads` | Parallel trackers (default 1) |
| `singleTracker` | Force single-threaded processing |
| `minIndex` | Start position for replay |
| `maxIndexExclusive` | Stop position for bounded replay |

### Error Handlers

| Handler | Behavior |
|---|---|
| `LoggingErrorHandler` (default) | Log and continue |
| `RetryingErrorHandler` | Up to 5 retries, then continue |
| `ForeverRetryingErrorHandler` | Unlimited retries with backoff |
| `ThrowingErrorHandler` | Stop tracker on failure |

---

## Event Handler Patterns

### Aggregate Injection (SDK 1.x)

```java
// Framework injects aggregate if event has matching typed ID field
@HandleEvent
void handle(PlaceOrder event, Order order) {
    // 'order' is injected based on event.orderId()
}
```

**Requirements for injection:**
- Event must have a typed ID field (e.g., `OrderId orderId`)
- Event must be linked to aggregate via `@Apply`
- String references (like `PaymentAccepted.reference`) don't work

### Explicit Loading (When Injection Fails)

```java
@HandleEvent
void handle(PaymentAccepted event) {
    // PaymentAccepted has String reference, not OrderId
    Order order = Fluxzero.<Order>loadEntityValue(event.reference());
    // ...
}
```

### Timestamp Injection

```java
@HandleEvent
void on(PlaceOrder event, Instant timestamp) {
    // timestamp is the event's creation time
}
```

---

## Web Endpoints

### Basic Handlers

```java
@HandleGet("/catalog")
List<Item> browse() { ... }

@HandlePost("/orders")
OrderId create(@Valid OrderRequest request) { ... }

@HandleGet("/orders/{orderId}")
Order get(@PathParam("orderId") OrderId orderId) { ... }
```

### Path Parameters

- `@PathParam` — URL path segment
- `@QueryParam` — Query string parameter
- `@HeaderParam` — HTTP header
- `@CookieParam` — Cookie value

### Auth Annotations

| Annotation | Effect |
|---|---|
| `@NoUserRequired` | Allow anonymous access |
| `@RequiresUser` | Require authenticated user |
| `@RequiresAnyRole({"admin"})` | Require specific role |
| `@ForbidsAnyRole("guest")` | Block specific role |

---

## External HTTP Calls

### SendWebRequest Pattern (Used in Meepleverse)

```java
public record ValidatePayment(PaymentId paymentId, ...) extends SendWebRequest<PaymentResult> {
    @Override
    protected WebRequest createRequest() {
        return WebRequest.post("https://psp.example/validate/" + paymentId).build();
    }
    
    @HandleCommand
    void handle() {
        PaymentResult result = sendWebRequestAndWait();
        if (result.accepted()) {
            Fluxzero.loadAggregate(paymentId).apply(new PaymentAccepted(...));
        } else {
            Fluxzero.loadAggregate(paymentId).apply(new PaymentRejected(...));
        }
    }
}
```

### Direct Web Requests

```java
WebRequest req = WebRequest.post("https://api.example/endpoint")
    .header("Authorization", "Bearer " + token)
    .payload(body)
    .build();
WebResponse res = Fluxzero.sendWebRequestAndWait(req);
```

---

## Testing

### TestFixture Basics

```java
TestFixture.create(handler1, handler2)
    .givenCommands(new CreateOrder(...))
    .whenCommand(new PlaceOrder(...))
    .expectEvents(PlaceOrder.class)
    .expectNoErrors();
```

### Key Methods

| Method | Purpose |
|---|---|
| `givenCommands(...)` | Set up prior state |
| `givenEvents(...)` | Seed with events |
| `whenCommand(...)` | Execute command |
| `whenEvent(...)` | Trigger event handler |
| `expectEvents(...)` | Assert published events |
| `expectNoErrors()` | Assert no exceptions |
| `expectExceptionalResult(Class)` | Assert specific exception |
| `andThen()` | Chain another test step |

### Async Fixtures

```java
TestFixture.createAsync(handler)
    .whenCommand(...)
    .expectResult(...);
```

Use `createAsync` for:
- `@TrackSelf` payloads
- Tracked consumers
- Real consumer infrastructure

### Mocking External Services

```java
@Consumer(name = "psp-stub")
class PspStub {
    @HandlePost("https://psp.example/validate/{paymentId}")
    WebResponse validate(@PathParam("paymentId") String id) {
        return WebResponse.builder().status(200).payload(new PaymentResult(true)).build();
    }
}

TestFixture.create(new OrderFulfillment(), new PspStub())
    .whenCommand(new ValidatePayment(...))
    .expectEvents(PaymentAccepted.class);
```

---

## Document Search

### Indexing

```java
// Aggregate with search
@Aggregate(searchable = true)
public record Item(@EntityId ItemId itemId, ItemDetails details) {}

// Manual indexing
Fluxzero.index(document, "collection-name");
Fluxzero.indexAndWait(document, "collection-name"); // Synchronous
```

### Querying

```java
List<Item> items = Fluxzero.search(Item.class)
    .lookAhead(term, "details.name", "details.description")
    .fetch(100);

// With facets
@Facet String category; // In the record

Map<String, Long> counts = Fluxzero.search(Item.class)
    .facetStats()
    .get("category");
```

### `@Facet` Annotation

Enables efficient counting and filtering:
```java
// Instead of fetching all and counting in Java:
.matchFacet("category", "Strategy")
.facetStats()
```

---

## Scheduling

### One-time Schedule

```java
Fluxzero.scheduleCommand(
    new ExpireReservation(reservationId),
    "reservation-expiry-" + reservationId,
    Duration.ofMinutes(15)
);
```

### Cancel Schedule

```java
Fluxzero.cancelSchedule("reservation-expiry-" + reservationId);
```

### Periodic Schedule

```java
@Periodic(delay = 60, timeUnit = TimeUnit.MINUTES)
public record CleanupTask() {
    @HandleSchedule
    void run() { ... }
}
```

---

## Common Patterns

### Fire-and-Forget Commands

```java
Fluxzero.sendAndForgetCommand(new BackorderItems(...));
```

Use when:
- You don't need the result
- The command will be handled asynchronously
- Failure handling is done via events

### Idempotent Event Handlers

```java
@HandleEvent
void handle(BackorderedItemArrived event) {
    Order order = Fluxzero.loadAggregate(event.orderId()).get();
    if (order.aborted()) return; // Guard against late events
    // ... proceed
}
```

### `@TrackSelf`

```java
@TrackSelf
public interface OrderUpdate { ... }
```

Makes every instance appear in the audit log. Use for:
- Commands that should be traceable
- Events that need visibility in Devboard

---

## Key Differences: SDK 1.x vs 2.x

| Concept | SDK 1.x (This Project) | SDK 2.x |
|---|---|---|
| State declaration | `@Aggregate` | `@Model` |
| Loading | `loadAggregate()`, `loadEntityValue()` | `loadModel()`, `loadGraph()` |
| Relationships | `@Member` (embedded) | `@Parent` (independent) |
| Navigation | `Entity<T>` | `Graph<T>` |
| History | Aggregate-level | Model-level |

**This project uses SDK 1.x.** Don't use `@Model`, `Graph<T>`, or `loadModel()`.

---

## Meepleverse-Specific Notes

### Consumer Layout

| Consumer | Handler | Purpose |
|---|---|---|
| `order-fulfillment` | `OrderFulfillment` | Orchestration |
| `order-projection` | `OrderProgressProjection` | Read model |
| `customer-notifications` | `CustomerNotifier` | Emails |

### Why Explicit Loading in OrderFulfillment

`PaymentAccepted`/`PaymentRejected` have `String reference`, not `OrderId`. Framework injection fails, so explicit `loadEntityValue(event.reference())` is required.

### Event Application Rules

- `PlaceOrder` → `loadAggregate().assertAndApply()` (creates Order)
- `PaymentAccepted` → `loadAggregate().apply()` (creates Payment)
- `ShipmentSent` → `loadAggregate().apply()` (creates Shipment)
- `BackorderedItemArrived` → `publishEvent()` (no aggregate, pure event)
