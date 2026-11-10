# Fluxzero Developer Guides — Complete Extract

*Compiled 10 November 2026 from the **Developer Guides** section of [fluxzero.io/docs/guides](https://fluxzero.io/docs/guides/). Every page in that section is covered. The content is condensed and rewritten as technical notes, not copied word for word. Each section links back to its source page. Code snippets are short illustrations built from the APIs each page describes.*

---

## Contents

**Part 1. Orientation**
- 1.1 Developer Guides overview
- 1.2 Core concepts
- 1.3 Fluxzero 2.0: overview
- 1.4 Fluxzero 2.0: deep dive

**Part 2. Tutorials**
- 2.1 Tutorials overview
- 2.2 Hello world
- 2.3 Building your first app (to-do)
- 2.4 Example: home automation
- 2.5 Example: ticketing

**Part 3. How-to guides**
- 3.0 Overview and SDK installation
- 3.1 Messaging (13 guides)
- 3.2 Modeling & persistence (7 guides)
- 3.3 Data protection (1 guide)
- 3.4 Testing (3 guides)

**Part 4. Reference**
- 4.0 Reference overview
- 4.1 Messaging (14 pages)
- 4.2 Modeling & persistence (10 pages)
- 4.3 Data control & serialization (3 pages)
- 4.4 Configuration (3 pages)
- 4.5 Error codes

---

# Part 1. Orientation

## 1.1 Developer Guides overview
Source: <https://fluxzero.io/docs/guides/>

- The Fluxzero SDK is for building and testing application behavior in **Java or Kotlin**.
- The guides cover the full path, from first concepts to specific implementation tasks.
- Entry points:
  - **Core concepts**: the programming model, explained through one small example.
  - **Fluxzero 2.0**: the new major version, its improvements, and how to migrate.
  - **Tutorials**: build a working app step by step.
  - **How-to guides**: complete one specific task.
  - **Reference**: SDK capabilities organized by subject.
- The wider docs site also has a "Building with Fluxzero" section (Devboard, cloud dashboard, deployment, access) and a "Tools & automation" section (CLI `fz`, Dev Server, MCP, agent plugins, GitHub Actions, System API). Those sections are outside this extract.

---

## 1.2 Core concepts
Source: <https://fluxzero.io/docs/getting-started/core-concepts/>

**Messages are the core abstraction.** There are three kinds:
- A **command** asks for a change.
- A **query** asks for information.
- An **event** reports that something happened.

You write the application code. Fluxzero delivers messages and stores your Models. The running example is a task you can create and mark complete. Java examples use Lombok `@With` for immutable copies; Kotlin examples use `copy`.

### Models
- A **Model** is state the application remembers and changes over time.
- Declare it with `@Model`. Mark its identifier with `@EntityId`.
- Use typed IDs: `TaskId extends Id<Task>`. The type parameter tells Fluxzero which Model the ID belongs to. A typed ID lets code address one task without loading all tasks.

```java
@Model
public record Task(@EntityId TaskId taskId, String name, @With boolean completed) {}

public class TaskId extends Id<Task> {
    public TaskId(String id) { super(id); }
}
```

### Commands
- A command names an action. Its `@Apply` method returns the new Model state.
- `CreateTask(TaskId, name)` has an `@Apply` with no Model parameter. It returns a new `Task`.
- `CompleteTask(TaskId)` has `@Apply Task apply(Task task)`. It returns `task.withCompleted(true)`.
- Send with `Fluxzero.sendCommandAndWait(...)`. In Kotlin: `sendCommandAndWait<Any?>(...)`.
- Fluxzero loads the Model by its typed ID, applies the change and stores the result. You do not need a forwarding handler.
- Command types must be registered:
  - Under Spring, they are found within the component scan.
  - `TestFixture` finds payload-side `@Apply` methods automatically.

**Rules and history**
- Bean validation (for example `@NotBlank`) checks input.
- `@AssertLegal` checks permission, such as who may complete a task.
- Both checks run before the change is stored.
- By default, accepted changes are kept as events and state is rebuilt from them (event sourcing). You can change the storage strategy later without changing what a command means.

### Queries
- A query payload implements `Request<T>`, where `T` is the result type.
- Example: `GetTask(TaskId) implements Request<Task>`. Its `@HandleQuery` method returns `Fluxzero.loadModel(taskId).get()`.
- Send with `Fluxzero.queryAndWait(...)`.
- To search by content, enable search on the Model and use the Model query APIs.

### Handlers

| Annotation | Reacts to |
|---|---|
| `@HandleCommand` | An action that needs explicit handling, such as calling an external service |
| `@HandleQuery` | A request for information |
| `@HandleEvent` | Something that happened |
| `@HandleGet`, `@HandlePost`, … | HTTP requests |
| `@HandleSchedule` | A scheduled message that is now due |

- Handlers can sit on the payload class itself. These run locally by default.
- Separate handler classes can group handlers for several message types. Register them explicitly, or let Spring discover `@Component` classes.
- **Events:** after a Model change is accepted, the applied command payload is normally published as an event. `CompleteTask` is a command when requested and an event once it has happened.
- Event handlers can receive the Model state as of that event, for example `@HandleEvent void on(CompleteTask event, Task task)`.
- Other consumers can react to the same event independently, for example to send a notification.
- **HTTP:** `@HandleGet("/hello") String hello()` returns a response body. Endpoints can send commands or queries to expose application behavior.
- **Scheduling:** a scheduled message, such as a reminder for tomorrow, reaches its `@HandleSchedule` handler when it becomes due.

### Tests
- `TestFixture.create()` runs behavior locally. You need no database and no platform mocks.
- Typical chain: `givenCommands(create, complete)` → `whenQuery(new GetTask(id))` → `expectResult(new Task(id, "Read the guide", true))`.
- Fixtures can also verify rejected actions, outgoing messages, HTTP responses and scheduled work.

### Related Models
- Models with their own identities link to each other with `@Parent`, for example a task belonging to a project.
- A **Graph** lets code navigate these links, for example to read a project together with its tasks.
- The to-do tutorial covers relationships, validation, authorization, queries and reminders.

---

## 1.3 Fluxzero 2.0: overview
Source: <https://fluxzero.io/docs/fluxzero-2/>

### The problem with 1.x Aggregates
A 1.x **Aggregate** forced four separate concerns onto one tree:

1. **Relationships.** A child (Task) could sit under only one root (Project). A second relationship, such as Sprint, had to cross the tree or be duplicated.
2. **Consistency.** An atomic commit covered one tree only. Moving a Task between Projects took two commits, which exposed an intermediate state. The Task's history also stayed in the old Project's stream.
3. **Persistence.** One tree had one history. Parts with different needs could not use different storage strategies, for example an event-sourced decision next to a frequently overwritten external status.
4. **Loading.** Addressing one `@Member` loaded the whole root, including unrelated members.

The usual workarounds both fail:
- A bigger root (a "god model").
- Smaller aggregates, which lose atomicity and need compensation logic.

Boundaries were also hard to change once events encoded them.

**What 2.0 changes:** the consistency boundary moves from the Aggregate to the **change itself**. Commands address an entity directly, and children point to parents through loose references.

### From Aggregates to Models

```java
// 1.x
@Aggregate record Project(@EntityId ProjectId projectId, @Member List<Task> tasks) {}

// 2.0
@Model record Project(@EntityId ProjectId projectId, ProjectDetails details) {}
@Model @With record Task(@EntityId TaskId taskId,
                         @Parent(pathInParent = "tasks") ProjectId projectId,
                         TaskDetails details, boolean completed) {}
```

- `pathInParent` names the collection the child appears in when Models are composed into a Graph.
- `@Parent` records ownership without embedding the child's state in the parent.
- In Kotlin, a 1.x member collection is annotated `@field:Member`.

### Each concern now has its own setting

**Independent state and history**
- Each event-sourced Model has its own event stream.
- `Fluxzero.loadModel(taskId)` loads only the Task. The 1.x calls `loadAggregate(...)` and `loadEntity(...)` rebuilt the whole root.
- The docs' illustration: completing a task after a cold load could mean replaying 100,000 Project events in 1.x, versus about 10 Task events with Models.

**Multiple parents**
- A Task can declare several `@Parent` fields, such as `projectId` and `sprintId`. It still exists once, with one identity and one history.
- A `@Parent` on a typed `Collection`, `List` or `Set` creates one edge per distinct ID.
- For shared children, add `deleteOnParentDeletion = false`. Otherwise, deleting any owning parent deletes the child.

**Moving between parents**
- Change the parent reference. The ID, history and children all stay the same.
- Example: `MoveTask` applies `task.withProjectId(destinationId)`.

**Independent extensions**
- A new feature can attach a Model under an existing one without changing it. Example: `InternalNote` with `@Parent(pathInParent = "notes") TaskId taskId`.
- The extension can live in a different application. That application needs only the relationship, not Task's implementation.

**Atomic changes across Models**
- One command can change several Models and commit all or nothing.
- Example: `CompleteTasks(firstTaskId, secondTaskId)` has two `@Apply` methods, each selecting its Task with `@Association("firstTaskId")` or `@Association("secondTaskId")`.
- `@InterceptApply` composes existing commands into one operation.

**Business rules across Models**
- `Graph<T>` replaces `Entity<T>` as the navigation and change interface. It offers `get()` and `childModels(Task.class)`, and loads lazily.
- Example: `AddTask` has `@AssertLegal checkCapacity(Graph<Project>)`, which rejects the command when the project already has 5 tasks.
- **Conflicts:** by default, Fluxzero retries against updated state. Reads made by assertions and applies are checked for conflicts even when those Models are not changed. Rules re-run, so an earlier approval never carries over silently.

**Rules shared across descendants**
- `@AssertLegal(cascade = true)` on a parent applies to changes in its descendants. Example: a Project's `assertOpen()` blocks Task changes in a closed Project.
- All enabled parent routes are checked, including both the old and the new route during a move.
- `@Parent(validateAncestors = false)` opts one relationship out.

**Persistence and caching per Model**
- Related Models can use different strategies. Example: `@Model(persistence = ModelPersistence.DOCUMENT, cached = false) TaskStatus`.
- Each status update replaces the document. It never appends to Task's history, and loading a Task does not load its status.
- Caching is on by default and can be turned off per Model.

### Working with Models and Graphs

**Automatic command handling**
- A registered command with a matching Model `@Apply` is handled for you: target resolution → validation → assertions → commit.
- 1.x handlers that loaded an Aggregate and called `assertAndApply` are no longer needed, and neither is a `@TrackSelf` command interface.
- Existing handlers can run the same pipeline explicitly with `Fluxzero.assertAndApply(command)`.

**Injecting related Models**
- Handler parameters can be Models selected by entity IDs in the message, or reached through relationships.
- Example: `GetTask.handle(Task task, Project project)`. The Project is found through the Task's parent.

**Historical Graphs**
- Inside a Model event handler, the Graph is frozen at that event. This applies to injected Graphs and to `Fluxzero.loadGraph(...)` calls inside the handler.

| Call | Returns |
|---|---|
| `task.get()` | State after the event |
| `task.previous().get()` | State before the event |
| `task.current().get()` | Latest state, which may differ (for example, the task was reopened later) |

**Graph-change handlers**
- Take `Graph<Project>` (or `Graph<Task>`) as the parameter to observe every change to the root and all its descendants, without listing event types.
- `previous()` gives the before-state.

**Deletion**
- Returning `null` from `@Apply` deletes the Model.
- Deletion cascades through `@Parent` by default. Opt a child out with `deleteOnParentDeletion = false`.
- Deletion is **logical**: current state is removed, event history is kept.
- Relationship constraints (these are conflict-checked Graph reads, not stored constraints):
  - `required = true`: the parent must exist.
  - `maxChildren`: limits children per parent, type and path.
  - `preventParentDeletion = true`: blocks deleting the parent while a matching child exists.
- **Physical erasure** is a separate operation:

```java
var repo = Fluxzero.get().modelRepository();
var plan = repo.planDeletion(approvalId, ModelDeletionCascade.NONE); // review the plan first
repo.deleteModel(plan).join();
```

  Published events and copies your application maintains have their own retention.

**Schedules owned by a Model**
- A scheduled command can carry a `@Parent` field, for example `RemindTask(@Parent TaskId taskId, Instant deadline)`.
- Schedule it with `Fluxzero.scheduleCommand(msg, new ScheduleId("reminder", taskId), deadline)`.
- Deleting the owner cancels the schedule. Cancellation is asynchronous, so the handler should still check that the work is relevant.

**`@Deadline`**
- A pure method on a Model that returns a one-shot `Schedule`, or `null` for none.
- Example: a `BookingHold` returns `new Schedule(new ExpireBookingHold(holdId), expiresAt)` unless it is confirmed.
- The schedule is created, replaced or cancelled atomically with the Model commit.
- `Graph.deadlines()` shows the recorded deadlines.

**Composed responses**
- Serializing a Graph nests descendants under their `pathInParent` names.
- Example: `@HandleGet("/projects/{projectId}")` can simply return `Fluxzero.loadGraph(new ProjectId(id))`. You need no embedded collection and no separate projection.

**Derived fields: `@GraphProperty`**
- A computed field that exists only in serialized output.
- Example: `@GraphProperty String projectName(Graph<Project> project)` on Task.
- It is not stored, so renaming the Project changes the next response without rewriting any Tasks.

**Audience filtering: `@FilterContent`**
- A Model method decides what a given caller sees. Return `this` to show the item, or `null` to hide it.
- Put `@FilterContent` on the endpoint to apply filtering across the whole composed response.
- Request authorization is a separate concern.

**OpenAPI**
- Use `@ApiDoc` on the class and `@ApiDocResponse(status = 200, modelGraph = Project.class)` on the endpoint. This produces a schema for the nested response.

**Search**
- `searchable` (default `false`) indexes node documents for a Model and its composed descendants.
- `@Parent(propagateSearch = false)` stops search propagation at that edge.
- `graphProjection.mode`:
  - `NONE` (default): Graphs are composed live at query time. Graph search still works.
  - `ASYNC`: also stores the full composed view and keeps it updated in the background.
  - `AWAIT`: also stores the composed view, and the commit waits until the view is updated.
- `DOCUMENT` persistence and `pathInParent` do not enable search on their own.
- Query: `Fluxzero.searchGraph(Project.class).match(true, "tasks/completed").fetchAll()`.
- Materialized projections replace hand-written projection handlers.

### Upgrading
- **Within 2.x:**
  - `searchable` now defaults to `false`. Turn it on only where needed.
  - Former document and projector settings are not source-compatible.
  - Changing annotations does not backfill indexes.
  - This is **not a rolling upgrade**. Coordinate all writers and Runtime instances.
- **From 1.x:**
  - Upgrade the SDK first. Stored state does not need to be migrated immediately.
  - Introduce Models for new state, or migrate Aggregates deliberately.

---

## 1.4 Fluxzero 2.0: deep dive
Source: <https://fluxzero.io/docs/fluxzero-2-deep-dive/>

> Note: this page is about 128,000 characters long. Everything readable up to the "Content filtering" part of the closing section is extracted here. The page's tail was cut off when fetched.

### 1.x comparison and boundaries
- In 1.x, `loadEntity(taskId, Task.class)` let an embedded `@Member` handle its own behavior, but the whole `@Aggregate` was still loaded and saved. Events, snapshots, cache entries and search documents all lived under the root.

| Type | State boundary |
|---|---|
| `Entity<T>` for an embedded `@Member` | The containing Aggregate or Model |
| `Entity<T>` for an independent `@Model` | Its own persisted state |
| `Graph<T>` | A navigation and read view over independently stored Models |

- `@Member` still exists for state you deliberately embed in one root.
- Moving from embedded members to independent Models is a **data migration**, not an annotation rename.

### Model declarations
- `@Model(name = "project")` sets an explicit logical name. The default is the simple class name, regardless of package. An explicit name survives class renames.
- Value objects such as `ProjectDetails(@NotBlank name)` are neither Models nor Members.
- Defaults: event-sourced, cached, no public search document, no periodic snapshots.
- The storage ID is the raw ID string with no prefix added, for example `ProjectId("launch")` → `launch`.
  - IDs must be unique across the Model repository. The Java ID type does not create a namespace.
  - Use `@EntityId(prefix = "status-")` when you need a distinct ID space.
- Types are discovered by the SDK's annotation processor. `@RegisterType` is not required.

### Commands that own behavior
**Pipeline:**
1. Receive the command.
2. Resolve the target and any requested Models or Graphs.
3. Run assertions and applies against the attempt's read state.
4. Check for conflicts and commit atomically.
5. Publish the applied payload as an event.

**Event publication:** the default is `IF_MODIFIED`, so a no-op change (for example, completing an already-completed task) publishes no event.

**Factory semantics:**

| `@Apply` signature | Behavior |
|---|---|
| No current-Model parameter | Create only; rejects duplicates |
| Required current-Model parameter | Requires existing state |
| `@Nullable` current-Model parameter | Upsert |
| Returns `null` | Logical delete |

### Injecting Models from message properties
- Works in command, event and query handlers, and also in assertions, interceptors and applies.
- **Resolution order:**
  1. A message property that matches the Model's `@EntityId`.
  2. Otherwise, a unique typed `Id<Task>` property.
  3. Ancestors are found through stored parent relations. Sibling Models are not loaded.
- Injecting a Model only reads it. It becomes a write target only if an apply returns a change for it.
- `@Association` controls which property binds to which parameter:
  - `@Association("leftId")` / `@Association("rightId")` separate two parameters of the same type.
  - `@Association("reference")` binds a property with a different name.
  - `@Association("tasks")` selects a relationship path.
  - Never rely on parameter order.
- **Which revision is read:**
  - Queries: current state.
  - Event handlers: state at that event.
  - Assertions and applies: the attempt's state.

### One-to-one companion Models
- Pattern: `@Model ProjectStatus(@EntityId(prefix = "status-") @Parent(pathInParent = "status") ProjectId projectId, boolean online)`.
- `PutProjectStatus.apply(Project, @Nullable ProjectStatus current)` is an intentional upsert.
- Load with `Fluxzero.loadModel(projectId, ProjectStatus.class)`.
- The ID scheme guarantees at most one companion. `@Parent(maxChildren = 1)` is the explicit equivalent.

### Multiple hierarchies and edge options
- Example: Task has `@Parent(pathInParent = "tasks") ProjectId` and `@Parent(pathInParent = "tasks", deleteOnParentDeletion = false) SprintId`.
- Deleting the Sprint removes the edge but does **not** set `sprintId` to null in the Task.

| Field declaration | Graph edge | Deleted with parent | Placement in composed output |
|---|---|---|---|
| Plain typed ID | No | No | No |
| `@Parent` | Yes | Yes | No named path |
| `@Parent(deleteOnParentDeletion = false)` | Yes | No | No named path |
| `@Parent(pathInParent = "tasks")` | Yes | Yes | Under `tasks` |

- Shorthand options: `required = true`, `maxChildren = N`, `preventParentDeletion = true`. The last one also disables cascade on that edge.
- **Collections of parent IDs:**
  - Null or empty means no parents.
  - Null elements, raw or nested collections, arrays and maps are rejected.
- `propagateSearch = false` excludes that branch from ancestor search Graphs and from document notifications.
- Recursive types are allowed. Cycles between actual Model identities are rejected.

### Business rules, atomic operations and concurrency
- **Rule on a Graph:** `AddTask` has `@AssertLegal checkCapacity(Graph<Project>)` using `children(Task.class).size() >= 5`.
  - For a simple count, `@Parent(required = true, maxChildren = 5)` does the same.
  - Keep the explicit assertion when you want a custom message or richer logic.
- **Ordered actions in one operation:** `@InterceptApply` can expand a wrapper (for example `AddTwoTasks`) into ordered `AddTask` actions.
  - Each action sees what earlier actions staged. If the second action breaks the limit, the whole operation is discarded.
  - Put assertions on the actions that write, not only on the wrapper.
- `children(...)` checks membership only. `childModels(...)` also reads the values into the decision's dependencies.
- **Conflict policies:**

| Policy | Behavior |
|---|---|
| `RETRY` (default) | Re-runs assertions and applies on fresh state |
| `FAIL` | Returns the conflict to the caller |
| `ACCEPT` | Keeps the decision and rebases the applies. Assertions do not re-run. |

- **Retry budget:** 3 retries after the first attempt, so 4 attempts in total. Configure with `fluxzero.model.maxConflictRetries` or `FLUXZERO_MODEL_MAX_CONFLICT_RETRIES`. Business rejections do not use up retries.
- **What counts as a read dependency:**
  - Tracked: injected Models, Graph values you inspect, and a synchronous `loadGraph(...)` inside a mutation.
  - Not tracked: `loadModel(...)` and document search.
- Keep retryable code free of external side effects. Rolling back a commit cannot undo a payment that was already sent.
- Routing by Project serializes commands for that Project. Conflict tracking is what protects decisions. Configure routing and consumer threads separately.

### Cascading rules
- `@AssertLegal(cascade = true)` checks every locally known parent route. A move checks both the old and the new route.
- Immediate rules see the logical state before the update. `afterHandler = true` sees the final atomic state.
- Using `ACCEPT` on a guarded attempt is changed to `FAIL`.
- Escape hatches:
  - `@Parent(validateAncestors = false)`: no inherited rules on that relation and its subtree.
  - `@Apply(ancestorValidation = AncestorValidation.DISABLED)`: no inherited rules for one mutation.
- Every application that writes to the protected scope must deploy the rule.

### Changing several independent Models
- Example: `PlaceOrder` has `@AssertLegal checkStock(Inventory)`, `@Apply createOrder()` and `@Apply reserve(Inventory)`. Order and Inventory have no parent link, yet both commit together.
- The event belongs to both Model streams but is published only once globally.
- **Commit path:**
  1. Stage the Model changes.
  2. Track the reads that were consumed.
  3. Validate conflicts.
  4. Commit events, state and relationship changes together.
  5. Graph projections are updated separately afterwards.

### Moves, ancestor updates and variable-size changes
- **Moving:** `MoveTask` checks the destination's capacity with `@Association("destinationId") Graph<Project>`. It skips the check when the destination is unchanged.
- **Identity choices when moving:**
  - A stable ID keeps the identity and only updates the edge.
  - An ID derived from the Project makes a move an identity migration.
  - Clearing an optional parent detaches the edge.
- **Updating an ancestor:** an apply on Task can return `project.withLastCompletedTaskId(taskId)`. Returning a replacement turns that read into a write.
- **Bulk changes:**
  - `@Apply List<Task> apply(Graph<Project>)` updates every child.
  - `@InterceptApply` can expand a list of IDs into individual `CompleteTask` actions.

### Loading and navigating

| Call | Returns |
|---|---|
| `Fluxzero.loadGraph(id)` | A lazy Graph |
| `get()` | The node's value |
| `childModels(Task.class)` | Child values |
| `children(Task.class)` | Child nodes you can navigate |
| `children("tasks")` | Children at that path |
| `children()` | All direct children |
| `namedChildren("task", false)` | Children by logical name, including types this app doesn't know |

- A DOCUMENT-only root is captured eagerly. Descendants stay lazy.

| Aspect | `loadEntity("task-a", Task.class)` (embedded) | `loadModel(taskId)` (independent) |
|---|---|---|
| Event replay | The whole root's history | The Model's own stream |
| Snapshots | Combined root state | Own value |
| Cache | The root tree | Own state |
| Search document | `@Aggregate(searchable = true)` indexes the combined root | Own state; composed documents are opt-in Graph materialization |

### Mixed persistence and document storage
- `ModelPersistence` options:

| Option | Keeps |
|---|---|
| `EVENT_SOURCED` | Retained events |
| `DOCUMENT` | Current state only |
| Both | History and a current document |

- Strict historical reads fail for an overwritten DOCUMENT-only value. Fallback views use the current value instead.

| Configuration | Events | Node document | Whole-Graph document |
|---|---|---|---|
| Plain `@Model` | Yes | No | No |
| DOCUMENT, not searchable | No | One unindexed internal document | No |
| `searchable = true` | Yes | One indexed per included node | Composed live |
| DOCUMENT + searchable | No | Indexed canonical node | Composed live |
| searchable + `ASYNC` or `AWAIT` | Yes | Yes | Stored projection |

- `SearchSettings(includeDescendants = false)` limits the search scope.
- `searchGraph(Project.class, true)` explicitly chooses live composition.

### Search API
```java
Fluxzero.search(Task.class).whereParent(projectId).match(true, "completed").fetchAll();
Fluxzero.search(Task.class).whereParent(Project.class, MatchConstraint.match("Launch", "details/name"));
Fluxzero.search(Task.class).whereAncestor(Project.class, /* constraint */);
Fluxzero.searchGraph(Project.class).match(true, "tasks/completed").fetchAll();
Fluxzero.searchGraph(Project.class).whereDescendant(Task.class, /* constraint */);
```
- **Trade-off:** a materialized projection pays its cost after commit and can lag; live composition pays at query time.
- Search reads current or projected documents. It is not transactional and not historical.
- Don't use manual indexing to edit domain state. Build separate read models for business projections.

### Reading state at event time
Timeline: a task is completed at 10:00, reopened at 10:05, and the handler runs at 10:10.

| Read | Sees |
|---|---|
| Graph injected into the completion event | Completed (the 10:00 state) |
| `previous()` | Open (before completion) |
| `current()` or `loadCurrentGraph(taskId)` outside a mutation | Open (after the 10:05 reopen) |
| Query injection | Current state |
| Assertion or apply injection | The attempt's state plus staged changes |
| Search | Documents, independent of the event |

- `previous()` needs retained event-sourced history.
- **State indexes:**
  - A state index marks a committed boundary, separate from the event-log index.
  - `graph.stateIndex()` is the read boundary. `revisionStateIndex()` identifies the selected revision.
  - `IndexUtils.timestampFromIndex(index)` decodes a timestamp.
  - `graph.atStateIndex(IndexUtils.maxIndexFromMillis(ms))` reads as of a point in time.
- **Strictness:**
  - `graph.strict(true)` gives exact historical values; `strict(false)` allows fallbacks.
  - `fluxzero.model.graph.strict=true` sets the application default.
  - Assertions, applies and replay are always strict.

### Graph-change and document handlers
- `@HandleEvent void projectChanged(Graph<Project> project, Message m, Metadata md)` receives every Graph transition under the root.
  - Requirements: the leading parameter is an unqualified `Graph<Project>`, with no explicit payload parameter, no association qualifier and no second Graph.
- Delivery:
  - Several changes under one root in one substep are delivered once.
  - Delivery is not exactly-once.
  - Creation has no `previous()`. Deletion gives an empty current Graph.
  - A move notifies both the old and the new root.
  - Physical erasure creates no event.
- `@HandleNotification` accepts the same signature without consumer segmentation. Use it for things like WebSocket UI refresh.
- `@HandleDocument void viewChanged(Graph<Project> p)` observes document updates for searchable Projects.
  - It may skip intermediate versions and has no `previous()`.
- Returning the unchanged Graph from a document handler migrates evolved nodes. `graphMigration = GraphMigrationTarget.PROJECTION` applies only to a stored composition.
- `playBackToCondition(g -> ...)` walks backwards through retained revisions to the first match. `atStateIndex` is the alternative for a known boundary.

### Sharing relationships across applications
- Two applications can share one Model repository. For example, a Notes app adds `@Model(name = "internalNote")` under Task, while the Project app doesn't have that class.

| Capability | Project app | Notes app |
|---|---|---|
| Read Task values | Yes | Needs the shared Task class |
| Read InternalNote values | Cannot deserialize them | Yes |
| Discover Notes under a Task | IDs and logical names only | Typed navigation |
| Handle Task commands | Yes | Should not register them |
| Handle Note commands | Should not register them | Yes |

- Unknown nodes:
  - `knownType()` is empty and `get()` fails explicitly.
  - Class-based selection counts only locally known types.
- Register write behavior **only in the owning app**. Broad Spring scanning can register handlers twice by accident.
- `loadCurrentModelState(...)` gives a verified read-only view of a shared Model's current document, without replay. It does not join a mutation's read set.

### Delayed work tied to its owner
- `RemindTask(@Parent TaskId taskId, Instant deadline)` scheduled under `ScheduleId("reminder", taskId)` is cancelled automatically when the Task is deleted. This emits `ScheduleAutoCancelled` metrics.
- Re-creating a deleted ID does not revive old schedules.
- `schedule.withParents(taskId)` sets ownership explicitly. With several owners, deleting any one of them cancels the schedule.
- Ownership does not keep deadlines in sync with state changes. Pausing or moving a deadline needs your own logic.
- **`@Deadline` details:**
  - Once the original time has passed, changing the payload does not create a new deadline. Return `null` first, then a payload, to start a new cycle.
  - A cron or delay setting picks the **next single occurrence**, not a repeating schedule.
  - A deadline method can inject a typed ancestor or `Graph<T>`. Prefer the narrowest one, because context changes re-evaluate affected owners.

### Atomic transformations without commands
```java
Graph<Task> observed = Fluxzero.loadCurrentGraph(taskId);
boolean ok = observed.compareAndSet(old.withCompleted(true));        // one attempt against this revision
observed.updateAndGet(cur -> cur.update(t -> t.withCompleted(true)), 2); // recompute on conflict; up to 3 attempts
```
- These calls skip `@Apply` and `@AssertLegal`. Check invariants inside the transformation and keep it free of side effects.
- `maxRetries` defaults to 0.

### Deletion and retained history

| Action on an Approval Model | Current value | Retained history |
|---|---|---|
| Mark completed | Still present, in completed state | Kept |
| Logically delete | Removed | Kept for reconstruction |
| Store snapshot | Unchanged | Kept; rebuilds faster |
| Run a physical deletion plan | Selected data erased | Selected history erased |

- With `deleteOnParentDeletion = false`, a surviving child keeps the deleted parent ID in its field. Navigation no longer shows that parent, so a parent ID is not proof of a live relation.
- `planDeletion(id, ModelDeletionCascade.DESCENDANTS)` follows the retained deleted-parent lineage.
- Inspect a plan before running it: `getModelCount()`, `getExternallySharedModelCount()`, `getSampleModelIds()`, `getPublishedEventCount()`.
- Erasure is precise but is **not** a blanket compliance guarantee. Published events, external copies and backups have their own retention.

### Graphs across the whole application
- `pathInParent` controls placement in output, not storage.
- `loadGraph` navigates authoritative state. `searchGraph` reads documents. They are **not interchangeable**.
- Graph JSON nests children with no SDK bookkeeping. A plain `Project` return value includes no children.
- `selectPaths("tasks")` or `selectPaths("tasks/notes")` makes a trimmed, immutable response view.
- Avoid returning unbounded trees.
- **Query results:**
  - A `Request<JsonNode>` query returns JSON both locally and when tracked.
  - A `Request<Graph<Project>>` query on a self-handling payload keeps the Graph when handled locally.
  - For queries that travel through tracking, declare a JSON or DTO response type.
- `@GraphProperty` runs only during Graph serialization. It is not authoritative and does not enforce integrity.
- `@FilterContent` example: an `InternalNote.visibleTo(User)` method returns `this` for users with the "team" role, and `null` otherwise.

---

# Part 2. Tutorials

## 2.1 Tutorials overview
Source: <https://fluxzero.io/docs/tutorials/>

The tutorials teach the SDK by building and testing an application step by step. They start with fundamentals, then move on to larger examples. Reading Core concepts first is recommended. Once you are building, use a how-to guide for a specific task or the reference for a single capability.

1. **Hello world**: create and call your first HTTP endpoint.
2. **Building your first app**: grow a to-do app with state, queries, web endpoints and tests.
3. **Example: home automation**: rooms, device controls, scenes, routines and sensor reactions, then an external API connection and a live UI.
4. **Example: ticketing**: reservations, payments and tickets, then admission, transfers and organizer workflows.

---

## 2.2 Hello world
Source: <https://fluxzero.io/docs/getting-started/hello-world/>

Goal: a working handler in about 3 minutes.

**Prerequisites**
- A Fluxzero project generated from the **Basic Starter**. See SDK installation (3.0) if you don't have one.
- The `fz` CLI.

**Steps**
1. Open the Basic Starter project in your editor. Its Spring setup discovers handlers in the root package.
2. Add `HelloEndpoint` in the root package:

```java
@Component
public class HelloEndpoint {
    @HandleGet("/hello")
    public String hello() { return "Hello from Fluxzero!"; }
}
```

   Outside Spring, register the handler instance yourself (see 4.4 Configuring Fluxzero).
3. Run `fz dev` in the project directory. Open the URL the CLI prints, add `/hello` to it, and you should see `Hello from Fluxzero!`.
4. Next: read Core concepts, then the to-do tutorial. The local development guide covers the Devboard and preview.

---

## 2.3 Building your first app (to-do)
Source: <https://fluxzero.io/docs/tutorials/first-app/>

### Setup
- Project "Todo", root package `io.fluxzero.todo`, Basic Starter (Java or Kotlin).
- Packages: `project.command`, `project.query`, `project.model`, `project.notifications`.
- The starter provides:
  - `Sender`, `UserId`, `Role` and `RequiresRole`.
  - Authentication setup.
  - `@RegisterType`, which gives short `@class` names in JSON.
- Build order: tasks → projects → search → HTTP endpoints → deadline notifications.

### Tasks
- `TaskId extends Id<Task>` serializes as a string and stays type-distinct from `ProjectId`.
- `TaskDetails(@NotBlank name)`. An optional `Instant deadline` is added later.
- `@Model Task`: `@EntityId taskId`, `@With details`, `@With assignee` (UserId), `@With completed`.
- `CreateTask(@NotNull taskId, @NotNull @Valid details)` has `@Apply create(Sender sender)`.
  - The assignee comes from `sender.userId()`, which is taken from message context, not from client input.
  - A duplicate create is rejected automatically.
- Test: `TestFixture.create().whenCommand(...).expectSuccessfulResult().expectThat(...)`, checking `Fluxzero.loadModel(taskId)`.

### Working with tasks: shared rules in an interface
- `interface TaskUpdate` declares `taskId()` and a default `@AssertLegal assertAuthorized(Task, Sender)`. It throws `UnauthorizedException("Unauthorized for action")`.
  - A missing task is rejected implicitly, because `@Apply` requires an existing Task.
- `AssignTask(taskId, newAssignee)` and `CompleteTask(taskId)` implement `TaskUpdate`.
- `CancelTask` has `@AssertLegal assertNotCompleted` (throws `IllegalCommandException("Task has already completed")`). Its `@Apply` returns `null`, which is a logical delete.

### Testing with JSON
- Test resources live in `src/test/resources/project/`, for example `create-task.json`, `assign-task.json` and `complete-task.json`.
- `@class` identifies the payload type. Use the fully qualified name if short names collide.
- `TaskFlowTest`:
  - `givenCommands(...)`.
  - `whenCommandByUser("alex", ...)` runs as a named user. The fixture's default sender is the system user.
  - `expectEvents(...)`.
  - Expecting `Entity.NOT_FOUND_EXCEPTION` for a missing task.

### Projects
- `ProjectDetails(@NotBlank name, @Size(max = 1000) description)`.
- `@Model Project`: `@EntityId id`, `@With details`, `ownerId`.
- `CreateProject` has `@RequiresRole(Role.MANAGER)`. Admins also pass through the role hierarchy. The SDK alternative is `@RequiresAnyRole("...")` with string roles.
- `ProjectErrors.unauthorized` is a shared `UnauthorizedException`.
- Task gains `@With @Parent(pathInParent = "tasks") ProjectId projectId`. It is nullable, so standalone tasks are allowed. The Project stores no task list.
- `interface ProjectUpdate` has `@AssertLegal assertAuthorized(Project, Sender)`, which checks the owner.
  - `UpdateProject(projectId, details)` implements it.
  - `AddTask(projectId, taskId, details)` implements it. Its `@Apply createTask(Project, Sender)` takes a Project parameter, which guarantees the parent exists.
- `TaskUpdate.assertAuthorized(Task, @Nullable Project, Sender)` now allows the assignee, the project owner or an admin.
- Tests:
  - `creatingProjectTwiceFails` expects `Entity.ALREADY_EXISTS_EXCEPTION`.
  - Rename tests cover NOT_FOUND and a non-owner being rejected.
  - Add and cancel a task, then check that `loadModel` returns null.

### Querying
- Project is annotated `@Model(searchable = true, graphProjection = @GraphProjection(mode = GraphProjectionMode.ASYNC))`.
  - The composed search document may lag behind writes.
  - Loads by identity return accepted state immediately.
- Queries:
  - `GetProject`: `Fluxzero.searchGraph(Project.class).match(projectId, "id").fetchFirstOrNull(...)`. Add `.match(admin ? null : userId, "ownerId")` to restrict access; `null` removes the filter.
  - `GetMyProjects`: `fetch(100, ...)` filtered by owner.
  - `SearchProjects(term)`: `lookAhead(term)` for autocomplete and full-text search.
  - `GetProjectTasks`: checks authorization, then `searchGraph(Task.class).whereParent(projectId).fetch(100, ...)`.
  - `FindMyTasks`: filtered by assignee. Task is searchable through its `@Parent` relationship.
- Tips:
  - Keep each handler inside its query class.
  - Have every query implement `Request<R>`.
- Tests:
  - `whenQueryByUser("someRandomUser", ...)` expects no result.
  - Chain steps with `.andThen()`.

### HTTP endpoints
- `ProjectEndpoint` is a `@Component` with `@Path("/projects")`. A leading `/` makes the path absolute, overriding the starter's `/api` package prefix.

| Method | Route | Does |
|---|---|---|
| `@HandlePost` | `/projects` | Generates an ID with `Fluxzero.generateId`, sends `CreateProject`, returns the ID |
| `@HandleGet` | `/projects` | Returns `GetMyProjects` |
| `@HandleGet("{projectId}")` | `/projects/{projectId}` | `@PathParam` → `GetProject` |
| `@HandlePut("{projectId}")` | `/projects/{projectId}` | Path parameter plus body → `UpdateProject` |
| `@HandleGet("tasks")` | `/projects/tasks` | Returns `FindMyTasks` |

- Every HTTP request is a message:
  - The web proxy logs it as a `WebRequest`.
  - Handlers answer with a `WebResponse`.
  - Handlers can inject `Sender` or `WebRequest`.
- Tests use `TestFixture.create(new ProjectEndpoint())` with `whenPost`, `whenPut` and `whenGet`.
  - The fixture does not validate bearer tokens. Configure an auth provider for real tokens.

### Scheduled notification
- `TaskScheduler` (`@Component`):
  - On `CreateTask` and `AddTask` events with a deadline, it calls `Fluxzero.schedule(new TaskExpiry(taskId), "task-expiry-" + taskId, deadline)`.
  - `@HandleEvent(allowedClasses = {CompleteTask.class, CancelTask.class}) handle(TaskUpdate)` calls `Fluxzero.cancelSchedule("task-expiry-" + taskId)`.
- `SlackNotifier` (`@Component`, `@ConditionalOnProperty("slack.webhook.url")`):
  - `@HandleSchedule notifyAssignee(TaskExpiry)` loads `Fluxzero.loadCurrentGraph(taskId)`.
  - If the task is still open, it posts to Slack with `Fluxzero.sendWebRequest(WebRequest.post(url).payload(Map.of("text", msg)).build())`.
  - The webhook URL comes from `ApplicationProperties.getProperty(...)`. Keep secrets in environment variables or a secret store.
- Outgoing web requests are messages too. They pass through the WebRequest log and the forward proxy, so they are observable.
  - Retries can duplicate posts. Use idempotency keys where the receiving service supports them.
  - A remote acceptance is separate from the Model commit. Check the returned future if later logic depends on it.
- Tests:
  - `TestFixture.create(new TaskScheduler(), new SlackNotifier()).withProperty("slack.webhook.url", "http://slack.test").atFixedTime(...)`.
  - `whenTimeElapses(Duration.ofDays(30))` → `expectWebRequests("/project/slack-notification.json")`.
  - Completing the task first → `expectNoWebRequests()`.

---

## 2.4 Example: home automation
Source: <https://fluxzero.io/docs/tutorials/home-automation/>

- Companion repo: `fluxzero-sample-home`. Clone it and run `fz dev`.
- The tutorial continues from the first app.
- Smoke test: sign in as alex, create a room, dim a light, activate the scene "A pleasant evening".
- The default profile stores device requests without needing real equipment.

### 1. Rooms
- `@Model(searchable = true) Home` has `HomeId`, `HomeDetails`, `ZoneId timeZone` and `HomeMode`. It implements `Place`.
- `@Model Space` has `@Parent(types = {Home.class, Space.class}, pathInParent = "spaces") PlaceId<?> parentId`, `SpaceDetails` and `DeviceId primaryLightId`.
  - The hierarchy comes from the parent relation, not from a room list stored on Home.
- `AddSpace` uses `@AssertLegal parentExists()`, which calls `Fluxzero.loadModel`.
- `MoveSpace` checks that the destination is in the same home. Devices keep their identities when a room moves.
- `DefineZone` groups rooms without moving them, for example "Downstairs".
- Residents:
  - `AddResident` creates a resident.
  - `ArriveHome` sets presence to `HOME`.
  - `ChangeHomeMode` is a separate command; arriving does not change the mode.

### 2. Devices
- `Device` has `@Parent SpaceId`, capabilities, measurements and pending settings.
- `DimLight(DeviceId, @Valid LightLevel)` implements `DeviceCommand`. Its `@Apply` adds the requested value to pending settings.
  - A shared rule on `Device` requires the device to support the capability.
  - A second `@Apply` records `requestedAt`.
- A command records a **request**. It does not fake an observation.
- `TurnOn`, `TurnOff`, `SetRoomTemperature` and the others follow the same pattern.

### 3. Observations confirm requests
- `DeviceStatus` is a child Model of the device. It holds observation time, availability, reported settings and readings.
- `ReportDeviceStatus` clears pending settings that the report confirms, using `withoutConfirmedBy`, but only when:
  - the device is ONLINE,
  - `requestedAt` is set, and
  - the observation is newer than the request.
- Older reports cannot replace newer ones.
- If the light is changed physically, the display follows the device and the stale request is not resent.

### 4. Scenes
- `Scene` (parent: Home) stores a list of `SceneAction`s, not device state.
  - Example action: `DimLights(SceneTarget, LightLevel)`.
  - Targets: `InSpace`, `OneDevice`, `InZone`, `WholeHome`.
- `ActivateScene` has `@InterceptApply prepare(Scene, Graph<Home>)`.
  - It builds a device → capability → command map. The last action for a given pair wins.
  - It returns all the device commands plus itself, so activation is **one atomic change**.
  - `@Apply(eventPublication = ALWAYS)` makes sure an event is published.
- If a target device is missing, activation throws `IllegalCommandException` and nothing changes.

### 5. Routines
- `Routine` (parent: Home) holds the scene reference, timing, enabled flag, `nextRun` and `generation`.
- `PlanRoutine` computes the next run in the home's time zone (`Once` or `Weekly`) and bumps `generation`.
- `RoutineSchedules` is annotated `@Consumer(name = "home-routine-schedules", singleTracker = true, errorHandler = ThrowingErrorHandler.class)`.
  - Its `@HandleEvent changed(Graph<Routine>)` either cancels the schedule or calls `scheduleCommand(new RunRoutine(...), ScheduleId.of("home-routine", id), nextRun)`.
- `RunRoutine` has a `@Parent RoutineId`, so deleting the routine cancels it.
  - Its `@InterceptApply` checks generation and deadline, then returns `ActivateScene` plus the progress update.
  - A `@HandleCommand` wrapper calls `assertAndApply`. If the scene is refused, it pauses the routine with a reason after the rollback.
- Tests:
  - `whenTimeElapses(60s)` → `expectNoErrors`, `expectNoSchedules`.
  - `PauseRoutine` removes the schedule.

### 6. Reactions to home and sensor changes
- `DefineAutomation` holds a scene, a trigger (for example `HomeBecomes(AWAY)` or a sensor trigger) and a cooldown.
- `HomeReactions` has `@HandleEvent observed(ReportDeviceStatus, Graph<DeviceStatus>, Message)`.
  - It finds the Home ancestor.
  - It reads the previous status with `status.previous()`, so no "previous temperature" field is needed.
  - It sends `ReactToHome`.
- `MeasurementCrosses(..., RISES_ABOVE, 24)` needs a previous reading at or below 24 and a new one above it.
  - Readings 23 → 25 activate the scene. A following 26 does not.
- `ReactToHome` applies the scene and the cooldown together, or pauses the automation with a reason.

### 7. Home Assistant integration
The integration has three separate jobs:
1. **Connect and discover:** `ConnectHomeAssistant` and `DiscoverHomeAssistantDevices`.
2. **Link:** `LinkHomeAssistantDevice` maps a Home device to provider entity IDs.
3. **Deliver and observe:** `HomeAssistantIntegration` delivers pending settings and polls for observations.

- `CallHomeAssistantService` has `@HandleCommand handle()`.
  - It POSTs to `api/services/{domain}/{service}` with `Fluxzero.sendWebRequestAndWait(request, REQUEST_SETTINGS)`.
  - `GatewayException` and `TimeoutException` are mapped to `HomeAssistantUnavailable`.
- `HomeAssistantEndpoint` resolves connection properties and the bearer token. These stay out of Models and commands.
- `GetHomeAssistantStates` reads `/api/states` and translates the result into `ReportDeviceStatus` messages.
- A successful service call does **not** clear pending settings. Only an observation does.
- Temporary failures are rescheduled at the connection's refresh interval.

### 8. Browser access and API
- `HomeAccess` grants view, control or manage rights per account. Being a resident does not imply control rights.
- `DeviceEndpoint` has `@HandlePost("/brightness") @ApiDoc(operationId = "dimLight")`.
  - Inputs: path parameters `homeId` and `deviceId`, `@Valid Percent`, `HomeUser`, `WebRequest`.
  - It calls `requireControl` (checks origin, permission, and that the device is in this home), then sends `DimLight`.
- Queries:
  - `FindDevices` uses `Fluxzero.search(Device.class).whereAncestor(homeId)`, which includes nested rooms.
  - `GetHomeOverview` assembles the view the UI uses.
- Frontend:
  - `Devices.jsx` sends `POST /api/homes/{homeId}/devices/{deviceId}/brightness` with `{ percent }`.
  - `home.js` shows pending and reported values separately.

### 9. Live UI
- `HomeSocket` at `/api/homes/{homeId}/live`:
  - When the socket opens, it authorizes the user and sends the overview.
  - `@HandleNotification changed(Graph<Home>)` calls `refresh()`, which re-checks session and permission. Expired access closes the socket.
- `App.jsx` opens a `wss:` or `ws:` socket after sign-in and replaces the displayed home on each message.
- Test files: `HomeTutorialTest`, `HouseExample`, `HomeModelTest`, `DeviceBehaviorTest`, `RoutineBehaviorTest`, `AutomationBehaviorTest`, `HomeAssistantTest`, `HomeWebTest`.

---

## 2.5 Example: ticketing
Source: <https://fluxzero.io/docs/tutorials/ticketing/>

- Repo: `fluxzero-sample-ticketing`. Run with `fz dev`.
- Demo: reserve two places for "Night Lights" at the Recital Hall. Sign in as `demo-organizer` for the Operations and Entrance screens.
- The local profile supports cash box-office sales. Stripe comes later.

### 1. Programme and seating
- Structure: `Venue` → `Hall` → versioned `SeatingPlan`. `Event` describes the show. `Performance` binds an event to a time and a plan.
- `Performance` is `@Model` with both EVENT_SOURCED and DOCUMENT persistence.
  - Two `@Parent(pathInParent = "performances")` relations: `EventId` and `SeatingPlanId`.
  - A `Cancellation` enum: `NONE`, `SETTLING`, `SETTLED`.
- Setup commands: `CreateVenue`, `CreateHall`, `RegisterSeatingPlan`, `CreateEvent`. `SchedulePerformance` requires a future start and a price for every section.
- Seating plans are immutable once registered. Prices live on the performance, so one hall can host dates at different prices.
- `GetProgramme`:
  - Title filter: `whereParent(Event, lookAhead(term, "details/title"))`.
  - City filter: `whereAncestor(Venue, match(city, true, "details/city"))`.
  - Sorting: `sortBy("details/startsAt")`, then `sortBy("performanceId")`.
  - Paging: `skip(offset).fetch(limit + 1)`. The extra row tells you whether there are more results.

### 2. Availability
- `SeatInventory` exists per numbered seat. `SectionInventory` holds standing capacity.
- `occupiedAt(now)`: a seat is occupied if it has a production hold, or a reservation that is sold or whose hold has not expired.
- Queries:
  - `GetAvailability` and `GetSeats` (a bounded slice of the layout).
  - `GetSeatSuggestions` follows `nextSeatId` links. It is query-only and reserves nothing.
- `ReservationRules` checks group composition, wheelchair and companion seating, and ticket-type eligibility.

### 3. Group reservation
- `ReserveTickets` uses `@InterceptApply`. It returns `ReservationHeld` plus `InventoryChanges.hold(...)`.
  - One `ChangeSeatInventory` per seat, or a count change for standing sections.
  - This is one Model operation, so if the last seat fails, the earlier seats roll back.
- Internal commands use `automaticHandling = DISABLED`. Clients can only submit `ReserveTickets`.
- Test `wholeGroup`:
  1. Alice reserves A2.
  2. Bob tries B1 and A2 → `IllegalCommandException`, and nothing is left behind.
  3. Bob reserves B1 and B2 → success.

### 4. Hold expiry
- A hold lasts 15 minutes, capped at the performance start.
- `ReservationDeadlines` schedules an owned expiry command and cancels it when the reservation leaves `HELD`.
- `ExpireReservation`:
  - `@NoUserRequired`, with `@Parent reservationId`.
  - `@InterceptApply ignoreStale` stops the command unless the reservation is still HELD and `expiresAt` has passed.
  - `@Apply` sets the status to EXPIRED.
- Inventory checks the deadline itself, so availability does not depend on exact timer timing.

### 5. Payments and tickets
- `Payment` is its own Model under Reservation.
- `StartPayment` checks ownership and an active hold.
- Only the payment integration may report money received.
- `RecordPaymentSuccess`:
  1. If the payment is already captured, the capture reference and amount must match ("Conflicting payment confirmation"). Nothing new happens.
  2. Otherwise, the payment is accepted only if the hold is still valid, the amount matches, the performance is not cancelled, and the money arrived before the start.
  3. It emits `PaymentCaptured`, plus `InventoryChanges.sell(...)` when accepted.
- `PaymentCaptured`:
  - Accepted → the reservation is CONFIRMED and one `Ticket` is created per admission.
  - Late → `REFUND_REQUIRED`. The financial facts are kept.

### 6. Stripe integration
- `BeginStripePayment` starts the flow.
- `StripePaymentProcess` is a `@Stateful` handler keyed by `PaymentId`. It tracks provider work separately from the business payment.
- `StripePaymentEffects` reacts to stored state and sends the next command or query.
- `CreateStripeIntent`:
  - `@LocalOnly`, `@RequiresAnyRole("PAYMENTS")`, implements `SendToStripe<StripeIntent>`.
  - It POSTs a form-encoded body to the payment_intents endpoint.
  - The stored `operationKey` is used as the Stripe idempotency key.
- Webhooks:
  - `ReceiveStripeWebhook` verifies the signature against the raw body, then publishes a correlated notification.
  - The process checks the account and the operation key, then **re-fetches** the intent rather than trusting the callback.
  - Redelivered webhooks must not duplicate tickets.

### 7. Delivery
- `ConfirmationRequests` compares the previous and current state to publish exactly one delivery request when a reservation becomes CONFIRMED.
- `ConfirmationDelivery` is stateful and keyed by reservation. It stores the recipient, acceptance and retry state.
- `ConfirmationEffects` sends the mail. A temporary failure schedules a retry and never undoes the purchase.
- `GetTicketPass` returns a signed credential. `TicketPdf` renders a printable pass.

### 8. Admission
- `CheckInTicket`:
  - `@RequiresUser`.
  - `@AssertLegal allowed(...)`: the ticket belongs to this performance, is VALID, the performance is not cancelled, and the gate is open.
  - `@AssertLegal staff(user)`: requires `Permission.ADMISSION`.
  - `@Apply` creates a `CheckIn` under the ticket.
- `RedeemTicket` verifies the signed credential and expands into `CheckInTicket`.
- A second scan fails with no events emitted.

### 9. Transfers
- `OfferTicketTransfer` creates an invitation. Ownership does not change yet.
- `AcceptTicketTransfer`:
  - Sets `customerId` to the recipient.
  - Bumps `credentialVersion` with `Math.incrementExact`, so old QR codes stop working.
  - The reservation, payment and invoice stay with the original purchase.

### 10. Cancellations and refunds
- `CancelReservation`:
  - Calls `InventoryChanges.release`.
  - Emits `ReservationCancelled`, which voids the tickets.
  - Emits `RefundRequired` if the reservation was paid.
- `ConfirmRefund` records the repayment and keeps the original capture.
- `RefundTickets` handles partial returns. A later full cancellation refunds only the remaining paid amount.
- `CancelPerformance` closes the performance. `PerformanceCancellation` settles all reservations (`SETTLING` → `SETTLED`).

### 11. Organizer tools
- `StaffAccess` is scoped per performance, with `MANAGE` and `ADMISSION` permissions. `SetStaffAccess` can only be used by an operator; an empty permission set revokes access.
- `StaffPermission` is checked on every managed query and command.
- Managed queries: `GetManagedPerformances`, `GetManagedOrders`, `GetManagedReservation`.
- Box office:
  - `ReserveBoxOfficeTickets` (channel `BOX_OFFICE`).
  - `RecordBoxOfficePayment` records a cash or external-terminal receipt and emits `PaymentCaptured`.
- Production holds:
  - `BlockProductionInventory` and `ReleaseProductionInventory`.
  - No expiry; released explicitly.

### 12. Waitlist
- `JoinWaitlist` records demand without touching stock.
- `OfferWaitlistPlaces` creates a normal reservation, holds its inventory, and links it to the waitlist entry. The offer gets the standard expiry and payment behavior.

### 13. Invoices
- `DraftInvoice` → `IssueInvoice`.
- `CreditInvoice`:
  - `@RequiresAnyRole("BILLING")`.
  - Requires the invoice to be ISSUED and the reservation to be CANCELLED.
  - Marks the invoice CREDITED and creates a `CreditNote`.
- Issued invoices are never rewritten.

### 14. Customer journey
- `BookingEndpoint` has `@HandlePost reserve(ReserveTickets, WebRequest)`. It calls `BrowserRequests.requireSameOrigin`, then `sendCommandAndWait`.
- Screens: `Performance.jsx` → `Purchase.jsx` → `MyTickets.jsx`.
- After every action, and while a hold or payment is pending, refresh state from the server. Browser countdowns are display only.

---

# Part 3. How-to guides

## 3.0 How-to overview and SDK installation

### How-to overview
Source: <https://fluxzero.io/docs/guides/how-to/>

- Each how-to guide shows one task with a concrete example. For setup, see SDK installation; for a guided introduction, see the tutorials.
- The overview groups the guides into four areas: Messaging (13), Modeling & persistence, Data protection (1) and Testing (3).
- The Modeling & persistence group on the overview also links the reference pages *State in Fluxzero*, *Models and Graphs*, *Stateful handlers*, *Store and query custom documents*, *Load a Model or Graph*, *Update Models*, *Work with Model relations* and *Explore history with Temporal Graphs*. It recommends starting with **State in Fluxzero** to choose between domain state, process memory and an application-owned view.

### SDK installation
Source: <https://fluxzero.io/docs/getting-started/installation/>

**Requirements**
- SDK 2.x requires **Java 25**. Compile with **JDK 25.0.3 or newer**.
- Java and Kotlin use the same artifacts and BOM.

**Ways to start**
- **With a coding agent:** complete the agent plugin setup. The agent picks the matching docs and runs local dev.
- **With the CLI:** generate a project (Maven or Gradle template; each has its own wrapper) and follow its build instructions.
- **Manually** in an existing project: configure the dependencies yourself, as below.

**Maven**
- Repositories (plain downloads, no account or credentials needed):
  - `<repositories>`: id `fluxzero`, url `https://packages.fluxzero.io/maven`, snapshots off.
  - `<pluginRepositories>`: id `fluxzero-plugins`, same URL.
- Older artifacts remain on Maven Central. **From 1 October 2026, new releases are published only to Fluxzero Packages.**
- BOM: import `io.fluxzero:fluxzero-bom` (type `pom`, scope `import`) in `dependencyManagement`, with version property `${fluxzero.version}`.
- Dependencies (versions come from the BOM):

| Artifact | Scope | Purpose |
|---|---|---|
| `io.fluxzero:sdk` | compile | The SDK |
| `io.fluxzero:sdk`, classifier `tests` | test | Test support |
| `io.fluxzero:test-server` | test, optional | In-memory Flux runtime |
| `io.fluxzero:proxy` | test, optional | In-memory Flux runtime |

- Annotation processing is required on Java versions above 21:
  - Add `io.fluxzero:sdk` to `annotationProcessorPaths` in `maven-compiler-plugin`, next to Lombok or any other processors.
  - Set `<parameters>true</parameters>`.

**Gradle**
- Add the Packages Maven repository **before** `mavenCentral()`. If repositories are centralized, put it in `dependencyResolutionManagement.repositories`.
- Only if you use Fluxzero Gradle plugins via `plugins {}`: add the repository to `pluginManagement.repositories` before `gradlePluginPortal()`, at the top of `settings.gradle(.kts)`.

```kotlin
implementation(platform("io.fluxzero:fluxzero-bom:$fluxzeroVersion"))
implementation("io.fluxzero:sdk")
testImplementation("io.fluxzero:sdk") { artifact { classifier = "tests" } }
testImplementation("io.fluxzero:test-server")
testImplementation("io.fluxzero:proxy")
annotationProcessor("io.fluxzero:sdk")
```

- Bump the BOM version once and every module stays aligned.

---

## 3.1 Messaging how-to guides

### Message replays
Source: <https://fluxzero.io/docs/guides/messaging/message-replays/>

- A replay means tracking from an earlier index in the message log.
- Uses: rebuilding projections, repairing missed or failed messages, adding features retroactively.
- Events are retained indefinitely by default, so replays are usually possible.

**Preferred: a new replay consumer**
```java
@Consumer(name = "auditReplay", minIndex = 111677748019200000L) // = 2024-01-01T00:00:00Z
class AuditReplayHandler {
    @HandleEvent void on(CreateUser event) { /* rebuild or audit */ }
}
```
- `minIndex` is inclusive.
- `IndexUtils.indexFromTimestamp(Instant)` converts a time into a log index.
- A new consumer bootstraps without touching live handlers.

**Resetting an existing consumer**
- `Fluxzero.client().getTrackingClient(MessageType.EVENT).resetPosition("myConsumer", replayIndex, Guarantee.STORED)`.
- Be careful: replaying through existing handlers can repeat side effects such as emails or HTTP calls. Use a dedicated consumer when side effects are not replay-safe.

**Other routes**
- `exclusive = false` lets one handler belong to a live consumer and a bounded correction consumer at the same time (see "Use one handler for live and historical events").
- To replay failed messages, see Dynamic dead-lettering.

### Dynamic dead-lettering
Source: <https://fluxzero.io/docs/guides/messaging/dynamic-dead-lettering/>

Use this to retry only the messages a handler failed on, after you've fixed the bug:
1. Deploy a consumer that tracks the **error log**.
2. Use `@Trigger` to recover the original message.
3. Filter by time window, payload type or source app, then re-send.

```java
@Consumer(name = "command-dlq",
          minIndex = 115126095052800000L,            // 2025-09-01
          maxIndexExclusive = 115295964364800000L)   // 2025-10-01
class CommandReplayHandler {
    @HandleError
    void retry(ConsoleError error, @Trigger(messageType = MessageType.COMMAND) MyCommand failed) {
        Fluxzero.sendCommand(failed);
    }
}
```
- Fix the root cause first.
- Limit the replay to the affected type and time window.
- Watch for partially completed external effects.
- You can add dedupe, transform or suppress logic before re-sending.
- Uses of the error log: fix bugs retroactively, test new handler logic on real errors, retry transient failures, filter out known false positives.
- A retry is a new publish, so normal routing applies. Routing decides which tracker processes related messages; it does **not** make retries idempotent.
  - Choose the routing key with payload fields or a class-level `@RoutingKey`.
  - A handler-level routing override needs `ignoreSegment = true` on the consumer.

### Model schedule reconciliation
Source: <https://fluxzero.io/docs/guides/messaging/model-schedule-reconciliation/>

Goal: delayed work follows the Model's **current intent**, so a stale event can never restore an old deadline. Related tools:
- **Parent-owned schedules** handle deletion only.
- **`@Deadline`** derives delayed work directly from Model state.

**1. Persist intent, then reconcile**
- Models:
  - `@Model Reminder(@EntityId ReminderId, @Parent ProjectId, Instant deadline, boolean completed)`.
  - `PlanReminder` creates it, `RescheduleReminder` replaces the deadline, `DeleteProject` returns null.
- Reconciler:

```java
@Consumer(name = "reminder-schedules", singleTracker = true,
          errorHandler = ForeverRetryingErrorHandler.class)
class ReminderSchedules {
    @HandleEvent void changed(Graph<Reminder> change) {
        Reminder current = Fluxzero.loadCurrentGraph(change.id(), Reminder.class, false).get();
        ScheduleId id = ScheduleId.of("reminder", change.id());
        if (current == null || current.completed()) Fluxzero.cancelSchedule(id);
        else Fluxzero.scheduleCommand(new RunReminder(current.reminderId(), current.deadline()), id, current.deadline());
    }
}
```
- One unqualified `Graph<Reminder>` parameter receives both direct changes and cascaded deletions.
- `singleTracker = true` serializes reconciliation across routing segments. Never write the same schedule from another consumer.
- Stable schedule IDs make replace and cancel repeatable.
- `ForeverRetryingErrorHandler` delays work during an outage instead of dropping it.
- `awaitOutgoingWrites = true` is the default.
- The `false` argument to `loadCurrentGraph` reuses the repository identity already on `change.id()`. `change.current().get()` is a shorter equivalent.
- The injected Graph is pinned to the event. `loadCurrentGraph` deliberately reads past it, so redelivering an old event cannot revive obsolete work.
- Schedule writes happen **after commit**, never inside `@Apply` or `@AssertLegal`.

**2. Guard the delivered command**
- `RunReminder(ReminderId, Instant expectedDeadline)`:
  - `@InterceptApply applicable(@Nullable Reminder current)` returns `null` (drop) if the reminder is missing, completed, its deadline changed, or `Fluxzero.currentTime()` is before the deadline.
  - `@Apply` marks it completed.
- Put clock-dependent decisions in `@InterceptApply`, not in replayable `@Apply`.
- If an ID can be deleted and recreated with the same deadline, add a **generation token** and check it too.

**3. `ifAbsent`**
- This example replaces the schedule each time; the current deadline wins.
- `ifAbsent = true` keeps an existing schedule even if its deadline is obsolete. It is not a once-only marker.

**4. Test the complete active set**
- `expectOnlyScheduledCommands(...)` checks only what was scheduled during `whenExecuting`.
- `expectOnlyActiveScheduledCommands(Predicate<Schedule>)` checks **all** active scheduled commands: ID, deadline and payload.
- `expectNoSchedules()` requires a local scheduling client. For a remote store, check known IDs with `messageScheduler().getSchedule(id)`.

**5. Test matrix**
- Cover: create, replace, complete, direct delete, cascade delete.
- Stale and early deliveries.
- Retry after a scheduling failure.
- `givenScheduledCommands` seeds state; it does not prove recovery on another instance.

### Tie schedules to their owner (parent-owned schedules)
Source: <https://fluxzero.io/docs/guides/messaging/parent-owned-schedules/>

- Put `@Parent` on the owner-ID field of the scheduled payload, for example `RunReminder(@Parent ReminderId reminderId, Instant expectedDeadline)`. Schedule with `Fluxzero.scheduleCommand(payload, "reminder-" + id, deadline)`.
- Rules:
  - Schedule from a tracked post-commit handler.
  - The owner must already be committed.
  - Never schedule inside `@Apply`.
  - A missing owner causes the schedule to be rejected.
  - The payload does not need to be a `@Model`.
- **Deleting the owner** (directly, by cascade, or while the app was offline) cancels its schedules. Updating, changing status or moving the owner does **not**.
- Cancellation is asynchronous and cannot recall delivered work, so the handler must still re-check.
- Multiple owners: deleting any one cancels the schedule.
- Null parents and `deleteOnParentDeletion = false` do not create ownership.
- **Explicit ownership:**
  - `messageScheduler().scheduleCommand(schedule.withParents(reminderId))`.
  - `withParents()` with no arguments makes the schedule unowned.
  - Prefer typed IDs. Explicit IDs are stored in metadata.
- A same-ID replacement also replaces ownership. An ignored `ifAbsent` request doesn't.
- Retries and periodic continuations keep the original owner's lifetime. Recreating an ID does not revive old work.
- Test:
  - `givenCommands(create)` → `given(fc -> fc.messageScheduler().scheduleCommand(...))` → `whenCommand(delete)` → `expectOnlyActiveScheduledCommands()` → `expectNoErrors()`.
  - Against a remote Runtime, wait for eventual cancellation.
- The `ScheduleAutoCancelled` metric is best-effort.
- Ownership needs scheduling-service support. Older or custom clients reject owned schedules.

### Custom message logs
Source: <https://fluxzero.io/docs/guides/messaging/custom-message-logs/>

- Use a custom log when a stream needs its own topic and retention, for example external meter readings. Domain changes belong in the normal command and event logs.
- Handle with `@HandleCustom("metering-points") void on(MeteringPoint p)`.
  - Custom handlers support everything native handlers do: `@Stateful`, results for requests, custom consumers, segment routing, metadata, tracking, tracing, retries, metrics and error handling.
- Publish with `Fluxzero.get().customGateway("metering-points").sendAndForget(new MeteringPoint("meter-1", 12.5))`.
- Retention per topic: `customGateway("metering-points").setRetentionTime(Duration.ofDays(90))`. This also works for the built-in `command`, `event` and `result` logs.

### Sending web requests
Source: <https://fluxzero.io/docs/guides/messaging/sending-web-requests/>

**One local command or query per external interaction**
```java
record GetPartnerOrder(@NotNull UUID orderId) implements Request<PartnerOrder> {
    @HandleQuery PartnerOrder handle() {
        var req = WebRequest.get("https://partner.example/api/orders/" + orderId)
            .header("Authorization", "Bearer " + ApplicationProperties.requireProperty("partner.api.token"))
            .build();
        var res = Fluxzero.sendWebRequestAndWait(req);
        if (res.getStatus() != 200) throw new IllegalStateException("Unexpected status " + res.getStatus());
        return res.getPayloadAs(PartnerOrder.class);
    }
}
// caller: Fluxzero.queryAndWait(new GetPartnerOrder(orderId))
```
- Choose command or query by what the operation means, not by its HTTP verb.
- Shared URL, header and mapping code can go in a helper or base class. Avoid a generic "HTTP command" envelope and injected API services.
- Read configuration through `ApplicationProperties` at the boundary. Validate base URLs.
- Local self-handling does not mean native HTTP. The nested request still uses the auditable proxy route.
- A local command is not a durable job. Add `@TrackSelf` or `@Consumer` only if you need Runtime delivery, a durable position, or independent replay or retry.
- External writes belong **after commit**, from a post-commit event handler. Never do them in `@Apply`.

**Responses and credentials**
- `getPayloadAs(...)` decodes the body. Gzip is decoded on the gateway route. Stream bodies can be read once.
- Auditlog masks `Authorization`, `Proxy-Authorization`, `X-Api-Key`, `Cookie` and `Set-Cookie` (case-insensitive) as `<value scrambled>`.
  - This masking is display-only. Secrets in URLs, bodies or logs are not covered.
- Store tokens in runtime config, for example `PARTNER_API_TOKEN`.

**Other ways to send**
- Async: `webRequestGateway().send(req)` returns a future.
- Fire-and-forget: `webRequestGateway().sendAndForget(Guarantee.STORED, req)`.

**Routing**
- **Absolute URLs** go through the Flux Web Proxy by default.
- **Relative URLs** (`/internal/task`) go to handlers in other connected Flux apps.

**Settings**
- `WebRequestSettings.builder()` options:
  - `.timeout(Duration)`
  - `.maxRetries(int)` (extra attempts, default 0)
  - `.retryDelay(Duration)`
  - `.retryableStatusCodes(Set.of(502, 503, 504))`
  - `.consumer("external-api-xyz")` to isolate outbound traffic
- All attempts share the `timeout`.
- `Content-Length` is set automatically for buffered HTTP/1.1 bodies.
- `.useNativeHttpClient(true)` runs the request directly from the app. It skips WebRequest/WebResponse logging, local handlers, interceptors and consumer isolation. Use it only deliberately.

**Testing**
- Stub the external URL in the fixture:
  - `@Consumer(name = "partner-api-stub") class PartnerApiStub { @HandleGet("https://partner.example/api/orders/{orderId}") WebResponse get(@PathParam("orderId") UUID id) {...} }`.
- Then:
  - `TestFixture.create(new PartnerApiStub()).withProperty("partner.api.token", "test-token")`.
  - `.whenQuery(...)` → `.expectResult(...)` → `.expectOnlyWebRequests(WebRequest.get(...).header("Authorization", "Bearer test-token").build())`.
- Use `createAsync(...)` for tracked flows.
- What fixtures don't test: DNS, TLS, proxy behavior and the real third-party contract.
- Add cases for non-2xx responses, malformed payloads, and timeouts or retries.

**Resilience controls (opt-in; existing apps keep `retryPolicy(LEGACY)`)**
- Retry policies:
  - `RetryPolicy.SAFE`: retries GET, HEAD, PUT, DELETE, OPTIONS and TRACE, or any request with exactly one non-blank `Idempotency-Key` header.
  - `RetryPolicy.IDEMPOTENT`: an explicit guarantee from you that the operation is idempotent.
  - `RetryPolicy.LEGACY`: the old method-independent behavior.
  - No policy retries unless `maxRetries > 0`.
- `retryJitter(true)` waits a random time between 50% and 100% of `retryDelay`. On the proxy route it needs an updated proxy.
- Per-origin concurrency caps (by scheme, host and port; local to each process, not cluster-wide):

| Transport | Property | Environment variable | Default |
|---|---|---|---|
| Proxy | `fluxzero.proxy.forward.maxConcurrentPerOrigin` | `FLUXZERO_PROXY_FORWARD_MAX_CONCURRENT_PER_ORIGIN` | 0 (off) |
| Native client | `fluxzero.web.native.maxConcurrentPerOrigin` | `FLUXZERO_WEB_NATIVE_MAX_CONCURRENT_PER_ORIGIN` | 0 (off) |

- Proxy: queued requests stay ordered per segment, and queue wait counts against the deadline. Set the per-origin cap below the global limit.
- Native: there is no queue. Going over the cap returns 503 immediately.
- Redirects keep the original request's permit. Use `SAME_ORIGIN` or `NEVER` if that distinction matters.

### Building API integrations
Source: <https://fluxzero.io/docs/guides/messaging/building-api-integrations/>

- Each external operation gets its own command or query, so callers never handle URLs, headers or JSON.
- Example: `record SetHeatingTarget(double celsius)` with a `@HandleCommand void handle()`.
  - It POSTs `{ "celsius": 21.0 }` to `https://heating.example/target` with a bearer token from `heating.api.token` (`HEATING_API_TOKEN`).
  - It expects HTTP 204.
  - Send with `sendCommandAndWait`. Never call `handle()` directly.
- Test at the HTTP boundary:
  - `@Consumer(name = "heating-api-stub")` stub with `@HandlePost("https://heating.example/target")` returning 204.
  - `expectOnlyWebRequests(...)` checks the method, URL, credential and body.
- **Growing the integration:**
  - Share auth and settings in a helper, but keep each operation its own command or query.
  - Let application state pick a trusted configuration group. Never accept URLs or tokens from public commands.
  - Keep provider-specific types in the integration layer.
- **Outcomes:**
  - Distinguish known refusals, temporary unavailability and malformed responses.
  - Never echo remote bodies or credentials to users.
  - A timeout may hide a remote success.
  - Retry only idempotent operations: "set to 21" is safe to repeat, "increase by 1" is not.
- **After commit:** domain command → commit → tracked handler → integration command → external API.
  - Keep I/O out of `@Apply`.
  - For work that must survive interruptions, store pending intent in a stateful workflow.
- **An acknowledgement is not an observation.** HTTP 204 doesn't mean the room reached 21 °C. Read observations separately.
- **Identity on repeat:**
  - Store an operation ID *before* sending, and map it to the provider's idempotency key.
  - Never generate a new key per attempt.
  - Respect the provider's key retention window. Use an authoritative lookup when the outcome is uncertain.
- Example code:
  - Home sample: `CallHomeAssistantService`, `HomeAssistantEndpoint`, `HomeAssistantRequestTest`, `docs/integration-boundary.md`.
  - Ticketing sample: `CreateStripeIntent` with `SendToStripe`, `StripePaymentEffects`, `StripeEffectRecoveryTest`.

### Handle external callbacks (webhooks)
Source: <https://fluxzero.io/docs/guides/messaging/receiving-webhooks/>

```java
class ProviderCallbacks {
    @HandlePost("/provider/status")
    WebResponse handle(ProviderStatus body, @HeaderParam("Authorization") String auth) {
        String expected = "Bearer " + ApplicationProperties.requireProperty("provider.callback.token");
        if (!expected.equals(auth)) return WebResponse.builder().status(401).build();
        if ("completed".equals(body.status())) Fluxzero.publishEvent(new OperationCompleted(body.operationId()));
        return WebResponse.builder().status(204).build();
    }
}

@Stateful
record AwaitingCallback(@EntityId @Association String operationId) {
    @HandleEvent static AwaitingCallback start(OperationStarted e) { return new AwaitingCallback(e.operationId()); }
    @HandleEvent AwaitingCallback complete(OperationCompleted e) {
        Fluxzero.publishEvent(new OperationFinished(operationId));
        return null; // deletes the waiting instance
    }
}
```
- Config: `provider.callback.token` (`PROVIDER_CALLBACK_TOKEN`).
- Store `OperationStarted` **before** calling the provider.
- `@Association` links the callback event to the waiting instance by operation ID. Once it is deleted, later duplicate callbacks find nothing to complete.
- Idempotency test:
  - `TestFixture.create(new ProviderCallbacks(), AwaitingCallback.class).withProperty(...).givenEvents(new OperationStarted("op-1"))`.
  - `.whenWebRequest(cb).expectEvents(new OperationFinished("op-1"))`.
  - `.andThen().whenWebRequest(cb).expectNoEventsLike(OperationFinished.class)`.
- Use HTTPS. For providers that sign callbacks, follow their signature protocol instead of a bearer token.

### Keep UIs in sync via WebSockets
Source: <https://fluxzero.io/docs/guides/messaging/updating-a-live-screen/>

```java
@SocketEndpoint
@Path("/tasks/{taskId}/live")
record TaskSocket(SocketSession session, TaskId taskId) {
    @HandleSocketOpen static TaskSocket open(SocketSession s, @PathParam TaskId taskId) {
        var socket = new TaskSocket(s, taskId); socket.refresh(); return socket;
    }
    @HandleNotification void changed(Graph<Task> task) {
        if (taskId.getId().equals(task.functionalId())) refresh();
    }
    void refresh() { session.sendMessage(Fluxzero.loadModel(taskId).get()); }
}
```
- Register `TaskSocket.class`, or let Spring discover it. Each connection gets its own instance.
- Browser side:
  - Build the URL with `new URL('/tasks/t1/live', location.href)` and switch the protocol to `wss:` or `ws:`.
  - In `onmessage`, parse the JSON. `null` means the task was removed.
- For private data, check access **on connect and before every update**.
- Notifications only trigger a refresh. They are not a durable history for the browser.

### Build a searchable projection from past events
Source: <https://fluxzero.io/docs/guides/messaging/adding-a-feature-to-history/>

```java
@Consumer(name = "purchase-history", minIndex = 0)
class PurchaseHistory {
    @HandleEvent void handle(PurchaseRecorded p) {
        Fluxzero.index(p, p.purchaseId(), "purchases").join();
    }
}
// query: Fluxzero.search("purchases").fetch(20, PurchaseRecorded.class)
```
- A new consumer name plus `minIndex = 0` processes the retained history first, then live events.
- Restarts resume from the saved position.
- The document ID is the purchase ID, so redelivery overwrites instead of duplicating.
- Results appear while the consumer is still catching up. Events past retention can't be replayed.

### Use one handler for live and historical events
Source: <https://fluxzero.io/docs/guides/messaging/live-and-historical-processing/>

- Live consumer: `@Consumer(name = "rewards-live", exclusive = false)` on `Rewards`.
  - It indexes `new Reward(id, amountInCents / 100)` with document ID = purchase ID, so writes are idempotent.
- Correction consumer, registered *before* `Rewards`:

```java
builder.addConsumerConfiguration(ConsumerConfiguration.builder()
    .name("rewards-correction-1")
    .handlerFilter(h -> h instanceof Rewards)
    .minIndex(fromInclusive).maxIndexExclusive(toExclusive)
    .build(), MessageType.EVENT);
```
- Keep the live consumer's name and registration order. Use fixed indices from `IndexUtils.indexFromTimestamp(...)`.
- Finishing:
  - Verify the corrected documents, then remove the correction config.
  - Use a **new name for each run**. Reusing a name resumes the old progress.
- This is safe only because each event owns exactly one result. A "latest balance" projection needs a separate rebuild collection.

### Split a consumer
Source: <https://fluxzero.io/docs/guides/messaging/splitting-a-consumer/>

Move `CustomerIndex` out of the shared `orders` consumer into `customer-index`, starting at index `cutover`:

```java
builder.addConsumerConfiguration(ConsumerConfiguration.builder()      // bounded config FIRST
    .name("customer-index").handlerFilter(h -> h instanceof CustomerIndex)
    .minIndex(cutover).exclusiveBeforeMinIndex(false).build(), MessageType.EVENT);
builder.addConsumerConfiguration(ConsumerConfiguration.builder()      // existing config, unchanged
    .name("orders").handlerFilter(h -> h instanceof OrderIndex || h instanceof CustomerIndex)
    .build(), MessageType.EVENT);
```
- `minIndex` is inclusive. `exclusiveBeforeMinIndex(false)` leaves earlier messages with `orders`.
- Steps:
  1. Pick a fixed **future** UTC time and convert it once with `IndexUtils.indexFromTimestamp`. Never recompute it at startup.
  2. Stop the old instances cleanly before the boundary, then start the new configuration.
  3. Once `orders` has worked through the pre-boundary backlog, remove `CustomerIndex` from its filter.
- If the boundary passes before you finish, pick a later one.
- Only suitable for work that tolerates redelivery and overlap. For strict ordering, finish the old work first.

### Merge two consumers
Source: <https://fluxzero.io/docs/guides/messaging/merging-consumers/>

Move `OrderIndex` (`order-index`) and `CustomerIndex` (`customer-index`) into one consumer, `indexes`, at `cutover`. Use three configurations:
- `order-index`: `handlerFilter(OrderIndex)`, `.maxIndexExclusive(cutover)`, `.exclusiveAfterMaxIndex(false)`.
- `customer-index`: the same, for `CustomerIndex`.
- `indexes`: filter on both handlers, `.minIndex(cutover)`, `.exclusiveBeforeMinIndex(false)`.

Notes:
- Remove any competing `@Consumer` annotations and keep other settings.
- Each old consumer resumes from its own position. No shared checkpoint is needed.
- Same cutover procedure as splitting. Remove the old configs only after **both** have caught up.
- **Don't** reset `indexes` to the furthest old position; that would skip the slower consumer's work.
- Messages must stay within retention for the whole handover.

---

## 3.2 Modeling & persistence how-to guides

### Designing Model state
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/model-state/>

- A `@Model` is independently addressable state with an `@EntityId`.
- Group cohesive data into plain immutable value objects, such as details or settings.
- Persistence, snapshots and Graph projections are configured separately.

**Express each change as an action**
- `CreateProject(@NotNull ProjectId, @NotNull @Valid ProjectDetails)` has an `@Apply` that returns `new Project(...)`.
- `RenameProject(@NotNull ProjectId, @NotBlank String name)` has an `@Apply` that returns `project.withDetails(details.withName(name))`, so other detail fields are kept.
- A command's shape follows the user's action, not the stored Model.

**Validation and rules**
- `@NotBlank` checks a field. `@Valid` on a nested parameter cascades validation into it. A constraint inside the nested type alone does not trigger the cascade.
- In Kotlin, use `@field:` targets.
- Bad serialized values can fail during deserialization, before validation runs.
- `@AssertLegal` handles rules that depend on current state or the user. Example: `assertOwner(Project, Sender)` throws `UnauthorizedException`.
- Set the owner from the authenticated sender, never from the payload.
- The SDK does not automatically validate every returned Model. Check combined candidate state inside `@AssertLegal`.

**Where each field belongs**

| Kind of field | Where it goes | Example |
|---|---|---|
| Descriptive business data | A cohesive details value | `ProjectDetails(name, description)` |
| Desired policy | A focused settings value | `NotificationSettings` |
| Identity | A typed ID on the Model | `@EntityId ProjectId` |
| Relationship | An explicit typed reference | `ownerId`, `@Parent workspaceId` |
| Current status | A field, or a state value for one invariant | `archived`, `status`, `completedAt` |

- Meaning decides placement. A requested delivery date is business input; a last-retry time is execution progress.
- Anything created, changed or deleted on its own schedule should be its own Model, linked with `@Parent`.
- Domain facts belong in Models; integration execution belongs in stateful workflows. For example, a payment capture or refund obligation is a Model, while provider attempt IDs, idempotency keys and retry progress belong in a workflow.
- Register write behavior only in the application that owns it. Spring discovers it within the scan; without Spring, register it explicitly.
- Renaming `name` to `details.name` changes the serialized shape and query paths. Plan upcasting or a migration and test it (see 3.4).

### Compose and update related Models (recipes)
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/model-recipes/>

All recipes use default event sourcing. None needs DOCUMENT persistence or a stored Graph.

**One-to-one companion**
- Pattern: `ProjectId` uses prefix `project-`. The companion is `@Model ProjectStatus` with `@EntityId(prefix = "status-") @Parent(pathInParent = "status") ProjectId projectId` and `boolean online`.
- For `ProjectId("example")`, the IDs are `project-example` and `status-project-example`.
- `PutStatus.apply(Project parent, @Nullable ProjectStatus current)`:
  - The `Project` parameter requires the parent to exist.
  - The `@Nullable` current parameter allows upsert. Without it, the factory is create-only.
- Load:
  - `Fluxzero.loadModel(projectId, ProjectStatus.class)`.
  - `loadGraph(projectId).childModels("status", ProjectStatus.class)`.
  - Passing only the typed ID loads the Project.
- A missing companion returns an empty Model or Graph. The alias fallback never treats the parent as the companion.
- At most one companion can exist, but none is required. Deleting the companion leaves the parent; deleting the parent cascades to it.

**One preference, several Graph views**
- Store the selection once: `Space.primaryLightId`.
- Derive it on the child: `@GraphProperty boolean primary(Graph<Space> space)` on `Device`. It runs only during Graph serialization; it is not a second source of truth.
- Commands:
  - `SelectPrimary` has `@AssertLegal validSelection(Graph<Space>)`, which rejects an ID that isn't a child Device.
  - `MoveDevice` and `DeleteDevice` use `requireUnselected(Graph<Device>)`, which rejects acting on the current primary.
  - Clear first with `sendCommandAndWait(new SelectPrimary(spaceId, null))`.
- Don't call `current()` or `loadCurrentGraph` inside a derivation; use the same pinned boundary.
- Concurrency is handled by normal conflict re-evaluation; no lock is needed.
- Alternative: a `primary` flag stored on the child, with an "at most one selected" invariant. Never keep both forms as independent truth.

**Assess, execute or orchestrate**

| API | Does |
|---|---|
| `Fluxzero.assertLegal(cmd)` | Runs interceptors and before-assertions. No apply, no commit. |
| `Fluxzero.assertAndApply(cmd)` | Runs the full Model pipeline as one atomic multi-Model action |
| `Fluxzero.sendCommandAndWait(cmd)` | Normal dispatch, routing and handler selection |
| `graph.assertAndApply(cmd)` | Writes only the selected Models. All applicable assertions still run, and their reads still count for conflicts. |

- A passing `assertLegal` is **not** a reservation. State can change afterwards, and `afterHandler` checks have not run yet. Normally, call only the execution API.
- Example: `ActivateProject` has two `@Apply`s (enable the Project, set the status) and `@AssertLegal(afterHandler = true) ready(ProjectStatus)`. If the after-check fails, both changes are rejected and no event is published.
- **Orchestration example:** `@Consumer(name = "project-launches") LaunchHandler`.
  - Its `@HandleCommand` sends `ActivateProject`.
  - It catches only `FunctionalException` and records `RecordLaunchOutcome(REJECTED)`. Otherwise it records STARTED.
  - Technical faults propagate.
- Rules:
  - A blocking nested command must not run on the same consumer that is waiting for it.
  - Two separate commits can be split by a crash. If both must succeed together, make them one Model action.
  - Keep I/O out of `@Apply` and `@InterceptApply`, because they re-run during replay and retry.

**A fresh view without losing history: `graph.current()`**
- Opens the same Model at a new current boundary. Affixes and companion IDs are kept.
- Returns a snapshot, not a live object.
- A moved node navigates to its new parents. A deleted node has an empty value.
- It does not carry over the injected view's read provenance, path selections, filters, or authorization and content filters. Keep invariant reads on the injected view.
- Use it for deliberate reconciliation, not as the default for command invariants.

**Checked replacement**
- `compareAndSet(replacement)`: one attempt against `revisionStateIndex()`. Returns `false` when the Model is absent or there's a conflict.
- `updateAndGet(fn, maxRetries)`: uses a fresh transactional Graph per attempt and returns the root after commit.
- `getAndUpdate(fn, maxRetries)`: same, but returns the root from before the update.
- These skip applies and assertions. The retry budget defaults to 0 and is independent of the Model conflict policy.
- A transport failure can leave the outcome uncertain.

**Consume-once**
- `getAndUpdate(Graph::delete)` with zero retries. Use the returned value only after the commit succeeds.
- A losing caller gets `ModelCommitConflictException` or `NoSuchElementException`.
- For external effects (OAuth, payments, workers):
  1. Persist a claim that carries an operation or generation ID.
  2. Call the provider only after the claim commits.
  3. Persist completion against the acknowledged claim revision.
- If the call fails, keep the claim for reconciliation. Use the provider's idempotency or status API where available.

### Validate and update Models atomically
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/preventing-double-bookings/>

```java
@Model record Seat(@EntityId SeatId seatId, boolean reserved) {}
record CreateSeat(SeatId seatId) { @Apply Seat apply() { return new Seat(seatId, false); } }
record ReserveSeat(SeatId seatId) {
    @AssertLegal void check(Seat seat) { if (seat.reserved()) throw new IllegalCommandException("Seat already reserved"); }
    @Apply Seat apply(Seat seat) { return new Seat(seatId, true); }
}
```
- Fluxzero loads the seat, checks it, and commits. If a competing change invalidates the read, the decision is retried against the newer state.
- Test:
  - `givenCommands(new CreateSeat(id), new ReserveSeat(id))`.
  - `.whenCommand(new ReserveSeat(id))`.
  - `.expectExceptionalResult(IllegalCommandException.class)`.
- The check and the change **must be in the same Model operation**. A separate availability query followed by an unconditional update leaves a race window.

### Correlate asynchronous responses with stateful handlers
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/waiting-for-a-response/>

```java
@Stateful
record AwaitingPayment(@EntityId String reservationId, @Association String providerReference) {
    @HandleEvent static AwaitingPayment start(PaymentStarted e) { return new AwaitingPayment(e.reservationId(), e.providerReference()); }
    @HandleEvent AwaitingPayment complete(PaymentConfirmed e) {
        Fluxzero.publishEvent(new ReservationPaid(reservationId));
        return null; // remove the finished process
    }
}
```
- Register `AwaitingPayment.class`. Fluxzero stores the instance a handler returns.
- `@Association` routes the later `PaymentConfirmed(providerReference)` to the right instance, even after a restart.
- Test:
  - `TestFixture.create(AwaitingPayment.class).givenEvents(new PaymentStarted("r1", "pay-42"))`.
  - `.whenEvent(new PaymentConfirmed("pay-42"))`.
  - `.expectEvents(new ReservationPaid("r1"))`.
- `PaymentStarted` must be stored before the confirmation arrives.
- Callbacks can arrive early or more than once. Publishing the event and removing the state are not one transaction, so the final action must tolerate retries.

### React to Model transitions with Graph history
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/reacting-to-a-change/>

- Goal: publish `RoomBecameCold` only when the temperature drops from 18 or above to below 18.
- `ReportTemperature.apply(@Nullable Room room)` handles the first report and every later one with the same method.

```java
class ColdRoomAlert {
    @HandleEvent void on(ReportTemperature event, Graph<Room> room) {
        var previous = room.previous();
        if (previous == null || previous.isEmpty()) return;          // first reading = baseline
        if (previous.get().temperature() >= 18 && room.get().temperature() < 18)
            Fluxzero.publishEvent(new RoomBecameCold(event.roomId()));
    }
}
```
- The injected Graph is bound to the triggering event, so the comparison stays correct even if newer readings are already stored.
- Test:
  - `givenCommands(report 20)`.
  - `whenCommand(report 17)` → `expectEvents(RoomBecameCold)`.
  - `andThen()`, `whenCommand(report 16)` → `expectNoEventsLike(RoomBecameCold.class)`.
- Redelivery can repeat the reaction, so handlers must tolerate duplicates.

### Choose a Model or Graph query
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/model-query-guide/>

**Three query shapes**
1. **By identity:** `Fluxzero.loadModel(id).get()`. No index needed.
2. **By content:** `@Model(searchable = true)` plus `Fluxzero.search(Task.class).match("open", "status").fetch(20)`.
   - Prefer bounded pages; use `fetchAll()` only when you need everything.
   - Search reads current indexed state. It has no event-time view.
3. **By relationship:** `Fluxzero.searchGraph(Project.class).whereChild(Task.class, MatchConstraint.match("open", "status")).fetch(10)` returns `List<Graph<Project>>`.
   - A searchable Project activates search for its composed Tasks.
   - `whereChild` selects roots but returns **all** their Tasks. Search `Task.class` directly to get only the matching Tasks.
   - A `@Parent` without a path supports navigation only. It does not embed the child or propagate search.

**Relationship filters**
- `search(Task.class).whereAncestor(projectId)` filters by ancestor ID. The ancestor does not need to be searchable.
- `whereAncestor(Project.class, match(...))` filters by ancestor content. Here the ancestor must be searchable.
- Available predicates: `whereParent`, `whereAncestor`, `whereChild`, `whereDescendant`.
  - Several constraints inside one `whereChild` must match the **same** child.
  - Separate `whereChild` calls may match different children.
  - Predicates select roots; they never prune the returned children.

**Three independent settings**
1. **Persistence:** EVENT_SOURCED or DOCUMENT. Events stay authoritative either way. An internal document does not make a Model searchable.
2. **Searchability:** `searchable = true` keeps a canonical indexed node and activates composed descendants by default.
3. **Materialization:** `@GraphProjection(mode = NONE | ASYNC | AWAIT)`. NONE (the default) composes live and still supports Graph search.

Other controls: `SearchSettings.includeDescendants`, `@Parent(propagateSearch)`. `searchable` defaults to `false`.

| Configuration | Authoritative load | Node document | Node/relation search | Graph search |
|---|---|---|---|---|
| Event-sourced, not searchable | Events | None | No | No |
| DOCUMENT, not searchable | Internal document | Internal, no index | No | No |
| Event-sourced, searchable | Events | Canonical indexed node | Yes | Live composition |
| DOCUMENT, searchable | Canonical document | Same indexed node | Yes | Live composition |
| Searchable + ASYNC or AWAIT | Unchanged | Same | Yes | Stored Graph (live override available) |

- Searchability is **not** an authorization boundary. For customized views, build a separate read model.
- `@Parent(pathInParent = "tasks", propagateSearch = false)` removes the branch from ancestor search Graphs and their notifications.
  - Navigation, serialization and deletion are unaffected.
  - The edge policy is persisted. Changing the annotation doesn't rewrite existing edges; migrate explicitly and rebuild.

**Stored Graph**
- The collection defaults to the logical name plus `-graphs`.
- `searchGraph(Project.class)` reads the stored Graph. `searchGraph(Project.class, true)` forces live composition.
- ASYNC may lag. AWAIT waits for affected projections but does not give multi-query transactions.

**Results and costs**
- `@SearchExclude` keeps a field in results but removes it from text matching.
- Graph results are complete documents, so large Graphs cost memory and bytes.
- For partial output: `.includeOnly("tasks").fetch(10, ObjectNode.class)`.
- Counts, grouped numeric statistics and facets cover the entire filtered selection.
- `fetch(10)` limits the roots returned, not the composition work. Relationship predicates narrow selection early.

**Migrating from removed APIs**
- `DocumentProjection` and `materializeGraph` are removed. Use `searchable = true`, `SearchSettings` and `GraphProjection` instead.
- Kotlin nested annotations omit the `@`: `searchSettings = SearchSettings(includeDescendants = false)`.
- The default canonical collection stays `$modelGraphComponents/<logical name>`. Don't copy an old public projection collection into `SearchSettings`.
- This is a coordinated upgrade; rolling upgrades with mixed writers are not supported.
  - First: stop old writers, migration consumers and subscribers, upgrade all Runtimes, and rebuild shared contract JARs.
  - Old `DocumentProjection`, `materializeGraph`, `HandleDocument.modelGraph` and `HandleDocument.modelState` no longer compile.
- Steps:
  1. Inventory sources and consumers.
  2. Keep canonical collection names stable.
  3. Backfill with `Graph.reindex()` from authoritative state, using a fixed cutoff.
  4. Reindex sources with an explicit schema revision.
  5. Re-register Graph definitions, for example `modelRepository().registerGraphProjection(Root.class, true).join()`, and wait until `graphProjectionStatus(Root.class).isRebuilding()` is false.
  6. Qualify all query types, including moves and deletes, before resuming writers.
- Setting `searchable = false` does not delete old data or unregister durable definitions.
- An idle instance checks for Runtime discovery every 5 seconds.
- All writers must share typed composition contracts. For polymorphic parents, include the concrete types in the writer's catalog.

### Update stored documents (`@HandleDocument`)
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/tracking-and-updating-documents/>

- `@HandleDocument void handle(UserAccount user)` observes the document log. Each (re)index gets a new message index based on the update timestamp.
- **Caveat:** when catching up, only the latest version of each key is seen. Use events when every transition matters.

**Transforming documents**
- A returned value replaces the stored document only if it has a **higher `@Revision`**.
- Example: `@HandleDocument("users") UserAccount upgrade(UserAccount old)` returning a copy with a normalized email.
- Metadata:
  - A plain return keeps the stored metadata and regenerates indexes.
  - Returning `new Message(user, Metadata.of("source", "migration"))` replaces the metadata entirely. `Metadata.empty()` clears it.
- Returning `null` deletes the document.
- Transformations survive restarts.
- Custom serializers that build new envelopes must call `DocumentMessageReader.retainSource(decoded, original)` for each output.

**Model documents**
- Handlers may persist **schema upgrades only**. Business changes and deletions go through commands.
- Use `@HandleDocument(source = DocumentSource.MODEL_STATE)` for internal, non-searchable state.

**Subscription types**

| Declaration | Observes |
|---|---|
| `@HandleDocument` with a `Task` parameter | The canonical searchable Task node |
| `@HandleDocument` with `Graph<Task>` | Task and its included descendants (NONE, ASYNC and AWAIT). Updates may coalesce; a deleted root arrives as an empty Graph. |
| `@HandleDocument(source = DocumentSource.MODEL_STATE)` | Internal Task state, even when not searchable |
| `@HandleDocument(documentClass = Task.class)` | An explicit node type |
| `@HandleDocument("myCollection")` | A named document collection |

**Materialized Graphs**
- Each node has its own type and revision, and `@Upcast` chains are applied lazily per node.
- There is no Graph-level upcaster; children don't inherit the root's revision.
- `@HandleDocument Graph<Project> rematerialize(Graph<Project> g) { return g; }` migrates changed nodes from verified canonical state.
  - Idempotent per node, not atomic across the Graph.
  - Needs a Runtime that supports the migration contract.
- `graphMigration = GraphMigrationTarget.PROJECTION` migrates only the stored composition.

**Full collection migration**
1. Bump `@Revision`.
2. Add `@Consumer(name = "reindex-users", minIndex = 0)` with a `@HandleDocument("users")` method that returns the upgraded document.
3. Deploy it, let it finish, then remove it.

- Use cases: upcasting legacy documents, fixing derived fields, cleaning deprecated data, rehydration jobs, current-state analytics.

---

## 3.3 Data protection how-to

### Protect and erase PII
Source: <https://fluxzero.io/docs/guides/data-control-and-serialization/using-and-erasing-pii/>

```java
public record CheckEmailDomain(@ProtectData String email) {}

public class EmailCheck {
    @HandleCommand(onMissingProtectedData = MissingProtectedDataPolicy.FAIL)
    @DropProtectedData
    boolean handle(CheckEmailDomain c) { return c.email().endsWith("@example.com"); }
}
```
- For a tracked command, `@ProtectData` stores the value separately and leaves a reference in the message. Fluxzero restores it for the handler.
- `@DropProtectedData` marks this handler as the final use and erases the retained value. Later processing finds it missing and fails explicitly because of the `FAIL` policy.
- Use `@DropProtectedData` only when no other handler or retry still needs the value. It is not a transaction.
- Test: `TestFixture.createAsync(new EmailCheck()).whenCommand(new CheckEmailDomain("alice@example.com")).expectResult(true)`.
- Erasing the protected value does **not** erase copies your own code made, including logs.

---

## 3.4 Testing how-to guides

### Testing your handlers
Source: <https://fluxzero.io/docs/guides/messaging/testing-your-handlers/>

**Pattern:** `given…` (prior state) → one `when…` (the action) → `expect…` (assertions). Runs locally with no database or platform mocks.

**Core API**
- Create: `TestFixture.create(handler1, handler2, StatefulHandler.class)`. Several handlers test combined behavior.
- Given:
  - `givenCommands`, `givenEvents`, `givenPost`, `givenScheduledCommands`, `givenModelEvents`, …
  - `given(fc -> ...)` for custom setup.
- When:
  - `whenCommand`, `whenQuery`, `whenEvent`, `whenWebRequest`, `whenGet`, `whenPost`, `whenPut`.
  - `whenCommandByUser(user | "id", ...)`, `whenQueryByUser(...)`.
  - `whenExecuting(fc -> ...)` for code outside the dispatch loop.
  - `whenTimeElapses(Duration)`, `whenTimeAdvancesTo(Instant)`.
  - `whenUpcasting(json)`.
- Expect:
  - `expectResult`, `expectSuccessfulResult`, `expectExceptionalResult(Class)`.
  - `expectEvents`, `expectNoEvents`, `expectNoEventsLike(Class)`, `expectSchedules(Class)`.
  - `expectCommands`, `expectOnlyCommands`.
  - `expectWebRequests`, `expectOnlyWebRequests`, `expectNoWebRequests`.
  - `expectWebResponse`, `expectWebResult`.
  - `expectNoErrors`.
  - `expectThat(fc -> ...)`, `expectTrue(...)`.
  - `expectOnlyScheduledCommands`, `expectOnlyActiveScheduledCommands`, `expectNoSchedules`, `expectNoNewSchedules`.
- Matchers: a class, a predicate, or a Hamcrest matcher (`isA(...)`).
- Chaining: expectations chain. `.andThen()` starts the next given/when step on the same fixture.
- Options: `withProperty(k, v)`, `atFixedTime(Instant)`, `registerCasters(...)`, `registerTypeAlias(...)`, `registerPackageAlias(...)`.

**JSON resources**
- Any given, when or expect argument that is a `String` ending in `.json` is loaded from the classpath.
  - Relative paths resolve against the test class's package; a leading `/` makes them absolute.
- The root needs `@class`.
  - With `@RegisterType`, short or partial names work. In Kotlin, enable `kapt` and annotate a marker object with `@RegisterType(root = "...")`.
  - Simple names are safe only if unique.
- Type aliases:
  - App-wide: `fluxzero.serialization.typeAliases` / `FLUXZERO_SERIALIZATION_TYPE_ALIASES`.
  - Programmatic: the builder, or `registerTypeAlias` / `registerPackageAlias` on the fixture.
  - Aliases apply to root and nested `@class` values.
- `@revision` next to `@class` represents an older payload. Both markers are stripped before upcasting.
- `@extends` merges another resource recursively; the current file overrides. Arrays and JSONL records resolve per element.
- `JsonUtils.fromFileWithTypeMapper(refClass, resource, serializer::resolveTypeName)` reads a resource using the fixture's type resolution.
- **Metadata:** wrap the payload as `new Message(payload, Metadata.of("roles", List.of("Customer", "Admin")))`.

**Users**
- You need the app's `UserProvider`.
  - The CLI starter registers a `SenderProvider` under `META-INF/services/io.fluxzero.sdk.tracking.handling.authentication.UserProvider`. Keep that file.
  - Or register the provider on the fixture's `DefaultFluxzero` builder.
- `whenCommandByUser("pete", ...)` resolves the ID through the provider. Passing a `User` object skips the initial lookup.
- Without a provider, the no-op fallback does not keep the user. If your provider loads profiles from storage, create the matching user during setup.

**Synchronous fixtures (default, `create`)**
- Same thread; fast and deterministic.
- **Consumer selection checks are on**, using the same consumer assignment, handler selection and serializer metadata as production. Tests therefore catch filters that would exclude a needed input.
- Turn off with `fluxzero.tracking.consumerFiltering=false` (`FLUXZERO_TRACKING_CONSUMER_FILTERING`) or per consumer with `consumerFiltering(false)`.
- `@LocalHandler` methods and local self-handlers skip the check, as in production.
- Not covered: `DispatchInterceptor.modifySerializedMessage`, retries, batching and delivery timing. Use async for those.

**Asynchronous fixtures (`createAsync`)**
- Use the real consumer infrastructure; `expect…` waits for outcomes.
- `@LocalHandler` methods still run synchronously.
- Newly discovered `@TrackSelf` payloads and automatic Model commands are registered first. If registration fails, create a new fixture.

**Spring**
- Spring Boot auto-configures `TestFixture`; inject it with `@Autowired` in a `@SpringBootTest`.
- Plain Spring: `@Import(FluxzeroTestConfig.class)`.
- The injected fixture is **async** by default. Switch with `fluxzero.test.sync=true` globally, or per class with `@TestPropertySource(properties = "fluxzero.test.sync=true")`.

**Schedules**
- `whenTimeElapses(Duration.ofDays(30))` or `whenTimeAdvancesTo(Instant)`, then `expectEvents(...)` or `expectNoEventsLike(...)` for cancellation.

**Cleanup**
- JUnit cleanup waits for owned resources but skips the response grace period for unanswered requests.
- Override with `fluxzero.shutdown.requestTimeoutMillis` (`FLUXZERO_SHUTDOWN_REQUEST_TIMEOUT_MILLIS`).

### Testing web endpoints
Source: <https://fluxzero.io/docs/guides/messaging/testing-web-endpoints/>

- No HTTP server or port is needed. Basic test: `TestFixture.create(endpoint).whenGet("/hello").expectResult("...")`.
- **POST:**
  - `whenPost("/games", "/game/game-details.json")` → `expectResult(GameId.class)` and `expectEvents(RegisterGame.class)`.
  - Handlers may return `CompletableFuture<T>`.
- **GET:**
  - `givenPost(...)`, `whenGet("/games")` → `.<List<Game>>expectResult(list -> list.size() == 1)` and `expectWebResponse(r -> r.getStatus() == 200)`.
  - Java needs the explicit generic type.
- **Cookies:**
  - Response cookies are kept and resent automatically.
  - `withoutCookies("name")` or `withoutCookies()` omits them. `withCookie(name, value)` adds one.
  - Explicit headers: `withHeader(...)` and `withoutHeader("Cookie")`.
- **Errors:**
  - Assert both the exception and the status, for example `expectExceptionalResult(IllegalCommandException.class)` plus a 403 `expectWebResponse`.
- **Path substitution:**
  - After `.andThen()`, placeholders such as `{gameId}` in later paths are filled from the previous results' `toString()`.
  - Substitution is by position, not by name or type. Example: `whenPost("/games/{gameId}/refund/{orderId}")`.
- **External stubs:**
  - Write an `@Consumer(name = "external-api-stub")` class whose `@HandleGet` uses the absolute URL.
  - `TestFixture.create(app, stub).whenGet("/app").expectWebResult(...).expectNoErrors()`.
  - Give the stub its own consumer, or the app and stub can block each other.
  - If you can't annotate the stub, select it with a `ConsumerConfiguration`. Register stubs before the first request.
- **Not simulated:** sockets, TLS, proxy behavior and transport envelopes. Native retry policy is applied but without real delays.
- **Real network:**
  - Start an embedded `TestServer` on `InetSocketAddress("127.0.0.1", 0)` and read the port from `ServerConnector.getLocalPort()`.
  - Stop the server in `finally`.

### Testing Model migrations
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/model-migration-tests/>

Example: move `Project.name` into `ProjectDetails.name`, keeping the logical name, identity and parent paths stable.

**Upcaster**
```java
class CreateProjectUpcaster {
    @Upcast(type = "com.example.CreateProject", revision = 1)
    ObjectNode upcast(ObjectNode payload) {
        JsonNode name = payload.remove("name");
        if (name == null || !name.isTextual()) throw new IllegalArgumentException("name missing");
        payload.putObject("details").put("name", name.asText());
        return payload;
    }
}
```
- The current command is `@Revision(2) record CreateProject(String projectId, ProjectDetails details)`.
- Don't default a missing name to a placeholder such as "Untitled". Define and test that case explicitly, or reject the data.
- Write one caster per changed top-level event, snapshot or Model type. An unchanged `RenameProject(id, name)` still needs wrapping during replay.
- A standalone fixture does not discover casters. Call `.registerCasters(...)`.

**Test**
- `TestFixture.create().registerCasters(new CreateProjectUpcaster())`.
- `.givenModelEvents("project-1", Project.class, "/project/create-project-rev1.json")`.
- `.whenExecuting(fc -> assertEquals(expected, Fluxzero.loadModel("project-1", Project.class).get()))`.
- `.expectNoEvents().expectNoErrors()`.

**Three separate proofs**

| Test | Proves | Doesn't prove |
|---|---|---|
| `whenUpcasting(oldJson)` | Old revision converts to the current value | Replay, stored bytes, indexes |
| `givenModelEvents` + load | Decoding, upcasting and `@Apply` reconstruction | Byte-preserving storage, restarts |
| Old writer → retained store → fresh reader | Artifacts really written across that boundary | A database upgrade, unless the database is exercised too |

**Old-writer qualification**
- Writer: `docs/developer/examples/model-migration/LegacyModelWriter.java`. Reader test: `ModelMigrationDocumentationTest` in `java-downstream-project`.
- Run the writer:
  - Command: `java --class-path "$WRITER_CLASSPATH" LegacyModelWriter.java "$TEST_URL" "$TEST_NAMESPACE"`.
  - Get the classpath with `dependency:build-classpath -Dmdep.outputFile=writer-classpath.txt`.
  - Never mix old and new SDK JARs, and never point it at production.
- Reader:
  - Create a new `WebSocketClient`, then `TestFixture.createAsync(builder, client)`, with no Given seeding.
- Embedded TestServer quirk:
  - Document logs are created lazily.
  - Open `getTrackingClient(DOCUMENT, collection).readFromIndex(0, 1)` before running the writer.
- Run the repository test with `-Dmigration.writer.classpath=...` to also qualify a pinned old SDK.

**Search before and after migration**

| Read | After upcaster only | After projection migration |
|---|---|---|
| `loadModel(id).get()…name()` | New value via replay | Unchanged |
| `previous()` | Old value | Unchanged |
| `search(...).match(v, "name")` | Matches the old path | Still matches (direct document not rewritten) |
| `search(...).match(v, "details/name")` | No match | Still no match |
| `searchGraph(...).match(v, "details/name")` | No match | Matches |

- Content selection happens **before** deserialization, so a caster can't make the server search an index path that doesn't exist yet.
- Projection migration: `@Consumer(name = "project-graph-schema-2", minIndex = 0)` with `@HandleDocument(graphMigration = GraphMigrationTarget.PROJECTION) Graph<Project> migrate(Graph<Project> g) { return g; }`.
  - Bump node revisions.
  - Register type aliases for renamed types.
  - A root must upcast to exactly one state.
- Source reindex: `@Consumer(name = "project-source-schema-2", minIndex = 0)` with `@HandleDocument(source = DocumentSource.MODEL_STATE) Project reindex(Project p) { return p; }`.
  - Identity, type and logical state must stay equal.
  - Returning `null` or changed business data is rejected.
  - A higher schema revision is required.
  - Guarded by compare-and-set on the durable head and proof, so concurrent changes turn it into a no-op.
- Order: migrate sources first, then Graph projections. Reindex children separately.

**Handler selection**

| Selection | Updates |
|---|---|
| `@HandleDocument` with `Project` | The canonical searchable node |
| `source = DocumentSource.MODEL_STATE` | Internal state, even for non-searchable DOCUMENT Models |
| `Graph<Project>` | Evolved canonical nodes, then stored Graphs |
| `graphMigration = GraphMigrationTarget.PROJECTION` | The stored composition only |

**`graph.reindex()`**
- Rebuilds a node's canonical search source from current state. It can recreate a missing source from full event history.
- Keeps the head, events, aliases and relationships unchanged.
- Does not enumerate children. Missing or deleted Models are no-ops; erased Models are not resurrected.
- Bounded run:
  - `@Consumer(name = "project-search-reindex", minIndex = 0, maxIndexExclusive = <fixed cutoff>, exclusiveAfterMaxIndex = false)`.
  - `@HandleEvent void on(Object e, Graph<Project> p) { p.reindex(); }`.
  - The cutoff is the fixed epoch milliseconds shifted left by 16 bits (`ms << 16`).
- Cutoff rules:
  - Use one fixed positive cutoff per run, and keep it on restart. Future or non-positive cutoffs fail.
  - Sources stored on or after the cutoff are skipped.
  - Stop old writers first.
- AWAIT completion waits for affected stored Graphs.
  - Precedence: consumer override → Model projection mode → `fluxzero.model.graphProjectionCompletion` (`FLUXZERO_MODEL_GRAPH_PROJECTION_COMPLETION`).
  - Upgrade the Runtime first.
- After migrating, cold-load the Model and assert `previous()` still returns the original value. This requires EVENT_SOURCED; DOCUMENT alone is not a historical archive.
- Extend your tests to cover snapshots, aliases, parent paths, document-only state, encryption and KV references, namespaces, mixed writers and service upgrades.

---

# Part 4. Reference

## 4.0 Reference overview
Source: <https://fluxzero.io/docs/reference/>

The reference is for looking up capabilities, annotations and configuration by subject. Each chapter starts with ordinary use, then covers more specific behavior. For complete tasks, use the how-to guides.

| Chapter | Topics |
|---|---|
| Messaging | Sending, Handling, Consumers, Scheduling, HTTP, WebSockets |
| Modeling & persistence | Updates, Relationships, Graphs, Persistence, Search, Stateful handlers |
| Data control & serialization | Data protection, Data evolution, Response filtering |
| Configuration | Application properties, Fluxzero builder, Client configuration |

Related: Error codes, the SDK Javadoc (`fluxzero-io.github.io/fluxzero-sdk-java/javadoc`), and Tools & automation.

---

## 4.1 Messaging reference

### Sending messages
Source: <https://fluxzero.io/docs/guides/messaging/sending-messages/>

**Which message kind to use**

| Intent | Kind |
|---|---|
| Change something | Command |
| Ask for information | Query |
| Announce that something happened | Event |
| Do work later | Schedule |

Messages are routed by **type**, not by address. The sender names the action and never needs to know where the handler runs.

**API**

| Call | Behavior |
|---|---|
| `Fluxzero.sendAndForgetCommand(p)` | Fire-and-forget |
| `Fluxzero.sendCommandAndWait(p)` | Blocks for the result |
| `Fluxzero.sendCommand(p)` / `sendCommand(p, Metadata.of(k, v))` | Returns `CompletableFuture<R>` |
| `Fluxzero.queryAndWait(q)` / `Fluxzero.query(q)` | Blocking or async query |
| `Fluxzero.publishEvent(e)` | Persisted in the event log. If a local handler exists, it is not forwarded unless `@LocalHandler(logMessage = true)` is set. |
| `Fluxzero.schedule(msg, Duration)` / `Fluxzero.schedulePeriodic(msg)` | One-off or periodic schedule |
| `Fluxzero.get().webRequestGateway().sendAndWait(WebRequest.get(url).build())` | Outbound HTTP through the Runtime |
| `Fluxzero.publishMetrics(m)` | Custom metric message |

- For persisted domain changes, prefer the Model APIs. They handle event storage and publication for you.

**Dispatch interceptors** (`DispatchInterceptor`)
- `interceptDispatch(Message, MessageType, String topic)`: modify, block (return `null`) or inspect.
- `modifySerializedMessage(...)`: change the serialized form.
- `monitorDispatch(...)`: observe the final message.
- Register: `builder.addDispatchInterceptor(i, MessageType.COMMAND, MessageType.EVENT)`.

**What happens after dispatch**
1. Pre-serialization interceptors run. They can enrich, validate or block.
2. A local handler, if one exists, is invoked immediately. If it is passive, or `logMessage = true`, the message is also forwarded.
3. Serialization (normally Jackson), with metadata and versioning.
4. Post-serialization interceptors run.
5. The message is forwarded to the Runtime, where guarantees, retries, rate limits and remote routing apply.

**Routing keys**
- Messages with the same `@RoutingKey` are handled in order; different keys run in parallel.
- Keys are hashed with consistent hashing into segments.
- Examples: `record ShipOrder(@RoutingKey OrderId orderId)`, or `@RoutingKey("customer/id")` on the class for a nested path.

**Timeouts**
- `@Timeout(value = 3, timeUnit = TimeUnit.SECONDS)` on a request class makes `queryAndWait` throw `TimeoutException`.
- Async `send` does not enforce it; use `orTimeout(...)` on the future.

**Delivery guarantees**
- SDK 2.x defaults to `Guarantee.STORED`. Configure with `fluxzero.publishing.defaultGuarantee` (`FLUXZERO_PUBLISHING_DEFAULT_GUARANTEE`), or per call: `sendCommand(p, Metadata.empty(), Guarantee.STORED)`.

| Guarantee | Waits for |
|---|---|
| `NONE` | Nothing |
| `SENT` | The message to be sent |
| `STORED` | Runtime storage |

- A storage acknowledgement does not mean a downstream handler has finished.
- Convenience methods such as `publishEvent` don't wait. Use the gateway overload that returns a future.
- Tracked consumers wait for registered outgoing writes before advancing their position.
- Processing is **at least once**, so make external effects idempotent.
- Re-sending a received message is a new dispatch. Carry correlation forward through metadata. A caller-supplied message ID is not an authenticated identity.

### Message handling
Source: <https://fluxzero.io/docs/guides/messaging/message-handling/>

- The annotation selects the message kind and the parameter type selects the payload. The return value becomes the response.
- Register handler classes yourself, or use Spring `@Component`. Inside a handler, static `Fluxzero.*` calls use the current application.
- Handlers can live on the payload class. For an external API call you do **not** need `@TrackSelf` or `@Consumer`.
- Returning `CompletableFuture<T>`: the result is published when the future completes. The consumer does not wait unless `@Consumer(awaitAsyncResults = true)`.

**Matching rules**
- Within one class, the most specific matching method wins. For example, `CreateUser` beats `Object`.
- Across different classes, every eligible handler runs.
- Commands, queries and web requests expect **one** active handler. Others can observe with `passive = true`, for example `@HandleQuery(passive = true) void count(Object q)`.

**Injectable parameters**
- The payload (by type).
- `Message`, `Schedule`, `WebRequest`.
- `DeserializingMessage`, `SerializedMessage`.
- `Metadata`.
- `Instant` or `Clock` (the message timestamp).
- `User`.
- `Entity`, or the entity value.
- `@Trigger` (the triggering message).
- Spring beans.

**Custom resolvers**
- Implement `ParameterResolver<DeserializingMessage>` and register with `builder.addParameterResolver(...)`.

**Handler interceptors**
- `HandlerInterceptor.interceptHandling(next, invoker)` wraps the call. It can check before calling `next`, block, or transform the result.

**Invocation flow:** match by signature → handler interceptors → resolve parameters → upcast and deserialize if needed → invoke.

### Message tracking (consumers)
Source: <https://fluxzero.io/docs/guides/messaging/message-tracking/>

**Concepts**
- A **consumer** is a named subscription to a message type, with its own log position and settings.
- A **tracker** is a worker thread of a consumer that fetches batches.
- A **handler** is assigned to a consumer.

**Defining consumers**
- `@Consumer(name = "activity-log", threads = 2, maxFetchSize = 100)` on a class, or in `package-info.java` to cover the package and its subpackages.
- Deployments are load-balanced across all trackers automatically.

**Default consumer for unassigned handlers**
- Precedence: explicit `@Consumer` → matching builder `ConsumerConfiguration` → mode fallback.
- Set the mode with `fluxzero.tracking.unconfiguredHandlerConsumerMode`.

| `fluxzero.defaults.version` | Default mode | Behavior |
|---|---|---|
| ≥ `2026.07.27` | `perPackage` | One consumer per handler package and message type |
| `2026.05.20` – `2026.07.26` | `perHandler` | One consumer per handler |
| Absent or older | `defaultAppConsumer` | A single shared application consumer |

- Automatic Model command handling uses the consumer for the command payload's package.

**Namespaces**
- `FLUXZERO_NAMESPACE` sets the app default.
- Override per consumer with `@Consumer(namespace = "...")`.
- `fluxzero.client().getNamespaces()` lists all namespaces. It needs Runtime support for the `/namespaces` endpoint and creates nothing.

**Consumer settings**

| Setting | Meaning | Default |
|---|---|---|
| `awaitAsyncResults` | Wait for async handler results before finishing the batch | `false` |
| `awaitOutgoingWrites` | Wait for outgoing writes before storing the position | `true` |
| `batchInterceptors` / `handlerInterceptors` / `dispatchInterceptors` | Interceptors for this consumer | `{}` |
| `clientControlledIndex` | The app chooses which messages to process | `false` |
| `durationUnit` | Unit for `maxWaitDuration` | `SECONDS` |
| `errorHandler` | Error policy | `LoggingErrorHandler` |
| `exclusive` | Handler is active in one consumer only | `true` |
| `exclusiveAfterMaxIndex` / `exclusiveBeforeMinIndex` | Keep exclusivity outside the bounds | `true` |
| `filterMessageTarget` | Only messages targeted at this instance | `false` |
| `flowRegulator` | Backpressure | `NoOpFlowRegulator` |
| `ignoreSegment` | Process all segments | `false` |
| `maxFetchBytes` | Byte limit per fetch (-1 = global) | `-1` |
| `maxFetchSize` | Messages per batch | `1024` |
| `maxIndexExclusive` | Upper bound (negative = none) | `-1` |
| `maxWaitDuration` | Idle wait | 15 min on supporting peers |
| `minIndex` | Start position (negative = log end, so only new messages) | `-1` |
| `namespace` | Consumer namespace | `""` |
| `passive` | Ignore results; don't publish to the result log | `false` |
| `singleTracker` | One tracker, strict global order | `false` |
| `storePositionManually` | App commits the position itself | `false` |
| `threads` | Trackers per instance, each with its own segments | `1` |
| `typeFilter` | Server-side type filter | `""` |

**Error handlers**

| Policy | Behavior | Use for |
|---|---|---|
| `LoggingErrorHandler` (default) | Logs technical failures as errors and functional ones as warnings, then continues without retry | The starting point. Monitor failures and plan replays. |
| `RetryingErrorHandler` | Up to 5 retries (immediate, then 2 s apart), then continues. Can be set to stop. | Transient failures with repeatable effects |
| `ForeverRetryingErrorHandler` | Unlimited retries: immediate, then 10 s backoff growing to a 1 min cap | Effects that must not be skipped and are idempotent. Add lag alerts. |
| `ThrowingErrorHandler` | Rethrows and **stops the tracker** until restart | Deliberate operator stops only |
| `SilentErrorHandler` | Continues; logging optional | Best-effort work |

- An initial `FunctionalException` is excluded from retries by default. Later failures follow `RetryConfiguration.errorTest`, which excludes `Error`.
- `stopConsumerOnFailure = true` stops tracking when retries are exhausted.
- Configure through `ConsumerConfiguration.errorHandler(...)`.
- A retry repeats the whole handler or batch. Effects completed earlier are not rolled back.

**Routing**
- There are 128 segments by default, split across trackers.
- `ignoreSegment = true`: each tracker sees everything.
- `singleTracker = true`: one tracker handles all segments.
- Handler-level key: `@Consumer(name = "order-processor", ignoreSegment = true)` plus `@HandleCommand @RoutingKey("paymentId")`.

**Tracking loop:** fetch batch → batch interceptors → deliver each message → invoke handlers and publish results → store position → stop at max index, or fetch the next batch.

**Consumer filtering**
- The Runtime drops messages no handler of the consumer needs. This is automatic on supporting Runtimes, the Test Server and local tracking.
- Overrides:
  - `fluxzero.tracking.consumerFiltering=false`
  - `fluxzero.tracking.maxWaitDuration=PT1M`
  - Environment variables: `FLUXZERO_TRACKING_CONSUMER_FILTERING`, `FLUXZERO_TRACKING_MAX_WAIT_DURATION`
- Selection is derived from payload parameters and `allowedClasses`. It stays broad for open types, unknown interceptors and custom metadata.
- Web routes are matched by `route` (method, origin and raw path); the Runtime never reads bodies.
- Opt in with `BatchInterceptor.preservingConsumerFilter(...)` for observers that tolerate omitted messages.
- Routed message envelope v2:
  - Upgrade all Runtime readers first.
  - Then set `fluxzero.runtime.messageEnvelopeVersion=2`.
- Incident rollback: run the Runtime with `fluxzero.runtime.consumerFiltering=false`.
- Unspecified idle wait: 15 min on new peers, 60 s on old ones.
- SDK 2.x: legacy aggregate roots must be annotated `@Aggregate`.

**Stopping**
- Cancelling a registration finishes the current batch, then sends `DisconnectTracker`.
- Shutdown grace periods are bounded (about 1–2 s per component). Handover after a crash is not exactly-once.

**Request-response via tracking**
- The requester publishes to the QUERY log. A responder tracks it and publishes to the RESULT log, which the requester tracks. The first stored result wins.
- With DOCUMENT persistence, a command followed by a query reads committed state. Graph projections and event-written documents stay asynchronous.

**Batch interceptors**
- Uses: per-batch logging, instrumentation, scoped transactions.
- `MappingBatchInterceptor` can filter or rewrite batches.

### Response typing
Source: <https://fluxzero.io/docs/guides/configuration/response-typing/>

- `Request<R>` on a query or command declares the response type, so `String s = Fluxzero.queryAndWait(new GetGreeting("Alice"))` needs no cast.
- Fixtures are typed too: `.expectResult(greeting -> greeting.startsWith("Hello"))`.
- The SDK annotation processor checks that handler return types match `R`.
- `Request<R>` does **not** register or create a handler.

### Local handling
Source: <https://fluxzero.io/docs/guides/messaging/local-handling/>

- `@LocalHandler` (on a class, method or package): runs in the publishing thread, is not persisted, and the caller doesn't wait for a tracker. Calls still go through the Fluxzero dispatch and handler APIs.
- `@LocalHandler(logMetrics = true)` enables metrics (off by default). `logMessage = true` also publishes the message.
- `@LocalOnly` (on a payload or package) only invokes local handlers.
  - `logMessage` is suppressed.
  - An unhandled request fails; an unhandled non-request completes quietly.
  - `@LocalOnly(false)` re-enables external fallback for a more specific package or type.
- **Self-handling payloads** are local by default and need no registration. Always call them through Fluxzero so validation and interceptors run.
- `@TrackSelf` is needed only for persisted Runtime delivery, its own consumer position, or independent replay or retry.
  - Not needed just to call an API, wait for a response, or get HTTP audit logs.
  - Combine with `@Consumer(name = ...)` for custom configuration.
  - Can be placed on an interface. Spring scanning discovers it.
- `@LocalOnly` does not register a handler. A Model-only command needs `@HandleCommand Object handle() { return Fluxzero.assertAndApply(this); }` to become process-local.

### Payload validation
Source: <https://fluxzero.io/docs/guides/messaging/payload-validation/>

- Jakarta constraints on command or query fields are checked **before** the handler runs. A violation throws `ValidationException`.
- Kotlin: use `@field:`.
- `@Valid` cascades into nested values but does not reject null; combine it with `@NotNull`.
- Collections: `@NotNull List<@NotNull @Valid ReminderDetails> reminders`.
- The built-in Jakarta implementation supports groups, custom validators, method constraints and nested collections. Hibernate Validator is not required.
- **Order:** field constraints (including container elements and cascades) are checked first, then method constraints such as `@AssertTrue boolean hasNonNegativeDelay()`.
  - Methods can rely on required fields being present.
  - Keep them pure; diagnostic runs after a field failure are suppressed.
- **Context-aware constraint methods:** `@AssertTrue boolean allowedBy(User user, Message message)`.
  - Parameters are injected like handler parameters.
  - If a parameter can't be resolved, the method is skipped.
- Configuration:
  - `builder.disablePayloadValidation()` or `builder.replaceValidator(...)`.
  - `ValidationUtils.assertValid`, `checkValidity` and `isValid` use the bound validator.
- Errors are correlated with the triggering message automatically. Keep messages user-facing and free of internal details.

### Role-based access control
Source: <https://fluxzero.io/docs/guides/messaging/role-based-access-control/>

| Annotation | Effect |
|---|---|
| `@RequiresAnyRole({"admin", "editor"})` | Needs at least one of the roles |
| `@ForbidsAnyRole("guest")` | Blocks if the user has any of the roles. With a non-empty list, an absent user gets `UnauthenticatedException`. |
| `@RequiresUser` | Needs an authenticated user |
| `@NoUserRequired` | Allows anonymous access (public endpoints, health checks) |
| `@ForbidsUser` | Blocks if a user **is** present (guest signup) |

- **On denial:**
  - No user → `UnauthenticatedException`; wrong roles → `UnauthorizedException`.
  - With `throwIfUnauthorized = false`, the handler is skipped silently. On a payload, every handler is skipped, and the caller may time out.
- **Resolution:**
  - Payload checks: payload type → own package → parent packages.
  - Handler checks: method → type → package → parents.
  - The nearest level with an annotation wins. Payload and handler policies combine with **AND**.
- Inherited annotations from parent types and interfaces count. With split packages across JARs, the first `package-info` found wins.
- Example: `com/myapp/web/package-info.java` has `@RequiresUser`, and a `@HandleGet("/health")` handler adds `@NoUserRequired`.
- **Enum roles:** meta-annotate a custom `@interface RequiresRole { Role[] value(); }` with `@RequiresAnyRole`.
- **Users:**
  - The `UserProvider` resolves users from metadata (headers or tokens).
  - `User.id()` is the stable identity and must be supplied explicitly. `getName()` defaults to `id()` and should only be overridden for display.
  - With `fluxzero.defaults.version >= 2026.08.04` or `fluxzero.auth.useUserIdMetadata=true`, only the ID is stored in metadata and users are resolved with `getUserById`.
  - Custom provider: extend `AbstractUserProvider` (`super("Authorization", MyUser.class)`) and override `fromMessage(HasMessage)`, for example to parse a JWT from a `WebRequest`.
  - `getSystemUser()` is used for scheduled handlers and tests.
- Register via SPI in `META-INF/services/io.fluxzero.sdk.tracking.handling.authentication.UserProvider`.

### Message scheduling
Source: <https://fluxzero.io/docs/guides/messaging/message-scheduling/>

| Call | Purpose |
|---|---|
| `Fluxzero.schedule(msg, "AccountClosed-" + id, Duration.ofDays(30))` | Schedule any payload |
| `Fluxzero.scheduleCommand(cmd, id, Duration \| Instant)` | Schedule a command |
| `Fluxzero.cancelSchedule(id \| ScheduleId)` | Cancel |
| `messageScheduler.schedule(schedule, false, Guarantee.STORED)` | Returns a future, so you can confirm storage |

- `ScheduleId.of("account-expiry", userId)` separates categories that share a domain ID. It is stored as `type:id`.
- Replay-safe pattern: schedule on `AccountClosed` and cancel on `AccountReopened`, using the same ID. The handler re-checks before acting.
- `@Parent` on the payload ties the schedule to its owner's lifetime (3.1).

**`@Periodic`** (on a schedule payload or a `@HandleSchedule` method)

| Attribute | Meaning |
|---|---|
| `delay` + `timeUnit` | Fixed interval |
| `cron` | For example `"0 0 * * MON"`. **UTC** unless `timeZone` is set. |
| `timeZone` | For example `"Europe/Amsterdam"` |
| `initialDelay` | `-1` (unset) by default |
| `autoStart` | `false` prevents activation at startup |
| `scheduleId` | Defaults to the payload class name |
| `delayAfterError` | Retry delay after a failure |
| `continueOnError` | `true` by default |

- **First run:**
  - In compatibility mode, an unset initial delay runs immediately.
  - With `fluxzero.defaults.version=2026.05.21` or `fluxzero.scheduling.periodic.useDefaultInitialDelay=true`:
    - A fixed delay first runs after `delay`.
    - A cron schedule first runs at the next match.
  - Set `initialDelay = 0` for an immediate first run.
- **Return values:**
  - `void` or `null`: same delay next time.
  - `Duration` or `Instant`: the next deadline.
  - A new `Schedule`: a new cycle.
- Throw `CancelPeriodic` to stop.
- Example: `@Periodic(delay = 60, timeUnit = MINUTES, delayAfterError = 10)` polls hourly and retries 10 minutes after a failure.
- Convenience methods don't wait for remote acknowledgement. A stored schedule is not an executed one.

### Handling web requests
Source: <https://fluxzero.io/docs/guides/messaging/handling-web-requests/>

- `@HandleGet("/hello") String hello()`; the return value is the body.
  - `@HandleGet` with no path serves `/`.
  - HEAD and OPTIONS are handled automatically.
- `@HandleWeb(value = "/users/{userId}", method = {"GET", "DELETE"})` handles several methods; branch on `request.getMethod()`. `HttpRequestMethod.ANY` (`"*"`) matches all.
- `@HandlePost(value = "/log", passive = true)` observes without responding.
- Return a `WebResponse` for explicit headers. `contentType(...)` replaces the type; `header(...)` appends values (useful for `Set-Cookie`).
- Returning `Graph<T>` gives composed JSON. Combine with `@FilterContent`.
- **Parameters:**
  - `@PathParam` is percent-decoded once as UTF-8: `order%3A1` → `order:1`, a literal `+` stays `+`, and `%2F` stays inside the value. Don't decode it again.
  - `@QueryParam`, `@HeaderParam`, `@CookieParam`, `@FormParam`.
  - Names are implicit and values are converted to the parameter type.
- **`@Path` composition:**
  - Works on packages, classes, methods and properties, from outermost to innermost.
  - A leading `/` resets the chain.
  - An empty `@Path` uses the package or class simple name.
  - On a property, the value becomes a dynamic segment.
  - Example: package `api` + class `@Path("users")` + method `@Path("{id}")` → `/api/users/{id}`.
- **Static files:** `@ServeStatic(value = "/assets", resourcePath = "/public", fallbackFile = "index.html", cleanUrls = true, immutableCandidateExtensions = {"js", "css", "svg"}, maxAgeSeconds = 86400)`.
  - The filesystem wins over the classpath. Restrict with a `classpath:` or `file:` prefix.
  - Content-based ETags; `If-None-Match` beats `If-Modified-Since`.
  - Serves precompressed gzip or Brotli files when present. Bodies are streamed.
  - Clean URLs try `<path>.html` and `<path>/index.html`.
  - Static and dynamic handlers can share a class; static routes apply only to unmatched paths.
- **Design:** the Web Gateway receives public HTTP(S) and WebSocket traffic and forwards it as messages. Your app exposes no HTTP server.
- **Proxy tuning:**

| Property | Purpose | Default |
|---|---|---|
| `fluxzero.proxy.responseHeaderBufferSize` | Initial HTTP/1 response-header buffer | 8192 |
| `fluxzero.proxy.http2MaxResponseHeaderSize` | HTTP/2 header limit | 16384 |
| `fluxzero.proxy.useOutputDirectByteBuffers` | Direct or heap output buffers | — |

  Oversized headers fail; they are never truncated.
- **Errors:**
  - `FunctionalException` messages are passed through, so treat them as public.
  - Malformed JSON → **400** with a fixed message.
  - Unexpected failures → **500** with a fixed message.
  - SDK auth-denial messages omit the user ID and handler name. Console reports include only the method and a bounded path.

### Handling WebSocket messages
Source: <https://fluxzero.io/docs/guides/messaging/handling-websocket-messages/>

- WebSocket traffic is forwarded by the Runtime into the WebRequest log.
- **Stateless (singleton) style:**
  - `@HandleSocketOpen("/chat") String onOpen()`.
  - `@HandleSocketMessage("/chat") String onMessage(String in)`.
  - `@HandleSocketClose("/chat") void onClose(SocketSession s)`.
- **Stateful style:** `@SocketEndpoint` + `@Path("/chat")` creates one instance per session (prototype scope), so fields can hold session state.
  - Use `session.sendMessage(...)` to send.
  - Can be combined with `@ServeStatic` on the same class.
- Other annotations: `@HandleSocketPong`, `@HandleSocketHandshake`.
- Keep-alive: the SDK pings every 60 s and closes the session if no pong arrives. Tune with `@SocketEndpoint(aliveCheck = @SocketEndpoint.AliveCheck(pingDelay = 30, pingTimeout = 15))`.
- `@Consumer` on socket handlers isolates tracking and replay.
- **Failures:**
  - `SocketRequest` failures get an automatic `SocketResponse.error` with the same request ID.
  - `FunctionalException` text is passed through; anything else becomes "Request failed".
  - Causes are logged internally.

### Message interceptors
Source: <https://fluxzero.io/docs/guides/messaging/message-interceptors/>

| Type | Phase | Interface method | Global registration |
|---|---|---|---|
| Dispatch | Before publishing or local handling | `interceptDispatch(Message, MessageType, topic)` (plus `modifySerializedMessage`, `monitorDispatch`) | `builder.addDispatchInterceptor(i, MessageType...)` |
| Handler | Around a handler call | `interceptHandling(Function<DeserializingMessage,Object> next, HandlerInvoker)` | `builder.addHandlerInterceptor(i, highPriority, MessageType...)` |
| Batch | Around a consumer batch | `intercept(Consumer<MessageBatch>, Tracker)` | `builder.addBatchInterceptor(i, MessageType...)` |

- Per consumer: `@Consumer(handlerInterceptors = ...)` and `@Consumer(batchInterceptors = ...)`.
- `highPriority = true` runs a handler interceptor before the others.
- `MappingBatchInterceptor` can filter a batch, for example dropping messages whose metadata contains `testOnly` and returning `batch.withMessages(filtered)`.
- Handler interceptor uses: authorization, auditing, validation, result transformation, thread-context propagation.

### Parameter resolvers
Source: <https://fluxzero.io/docs/guides/messaging/parameter-resolvers/>

- Handler parameters are resolved by type and annotation; declare only the ones you need.
- Built-in parameters: payload, `Message`, `Schedule`, `WebRequest`, `DeserializingMessage`, `SerializedMessage`, `Metadata`, `Instant` or `Clock` (timestamp), `User`, `Entity` or its value, `@Trigger`, and Spring beans.
- Custom resolver:

```java
public class RequestOriginResolver implements ParameterResolver<DeserializingMessage> {
    public Function<DeserializingMessage, Object> resolve(Parameter p, Annotation a) {
        return p.getType().equals(RequestOrigin.class)
            ? m -> new RequestOrigin(m.getMetadata().get("source")) : null;
    }
}
// builder.addParameterResolver(new RequestOriginResolver());
// @HandleCommand void handle(CreateOrder c, RequestOrigin origin) { ... }
```
- Return `null` for parameters you don't handle, so other resolvers get a turn.
- Metadata such as a source label is descriptive, not a verified identity.

### Metrics messages
Source: <https://fluxzero.io/docs/guides/messaging/metrics-messages/>

- `MessageType.METRICS` messages are logged like any other message.
- Publish:
  - `Fluxzero.publishMetrics(new SystemMetrics(...))`.
  - `Fluxzero.get().metricsGateway().publish(m, Metadata.of("critical", "true"), Guarantee.STORED)`.
- Consume: `@HandleMetrics void on(HandleMessageEvent e)`.
  - `HandleMessageEvent.completed = false` means async work is pending; `CompleteMessageEvent` gives the final outcome.
- Host and JVM metrics:
  - Enable with `Fluxzero.builder().enableHostMetrics().build(client)`.
  - Every 60 s; disk collection is off by default. Configure with `HostMetricsConfiguration`.
- WebSocket transport diagnostics: `fluxzero.websocket.transportMetrics.enabled=true`.
- **Built-in metric families:**
  - Dispatch: `Append$Metric`, `SetRetentionTime`.
  - Tracking: `Read`, `ReadResult$Metric`, `PauseTrackerEvent`, `ClaimSegment`, `ProcessBatchEvent`, `HandleMessageEvent`, `CompleteMessageEvent`, `IgnoreMessageEvent` (skipped after expiry), `ReadFromIndex`, `GetPosition`, `StorePosition`, `ResetPosition`, `DisconnectTracker`.
  - Model: `CommitModels$Metric` and result (accepted, duplicate, rebase, conflict), `GetModelEvents`, `GetModelGraph`, `GetModelChange`, `PlanModelDeletion`, `DeleteModel`, `TrackModelUpdates`, `RegisterModelGraphProjection`, `GetModelGraphProjectionStatus`, `AwaitModelGraphProjection`.
  - Relationships: `GetRelationships`, `UpdateRelationships`, `RepairRelationships`, `GetAggregateIds`. Event store: `AppendEvents`, `GetEvents`, `DeleteEvents`.
  - Scheduling: `Schedule$Metric`, `CancelSchedule`, `GetSchedule`, `ScheduleWithParents$Metric`, `GetScheduleParents$Metric`, `ScheduleAutoCancelled`.
  - Documents and search: `IndexDocuments`, `SearchDocuments` and result, `GetDocument(s)`, `HasDocument`, `DeleteCollection`, `DeleteDocuments`, `MoveDocuments`, `BulkUpdateDocuments`, `GetFacetStats`, `GetDocumentStats`, `GetSearchHistogram`, `GetSearchCollections`, `SearchModelDocuments`, `SearchModelGraphDocuments`.
  - Key-value: `StoreValues`, `StoreValueIfAbsent`, `GetValue`, `DeleteValue`.
  - Results: `VoidResult`, `ErrorResult`, `BooleanResult`, `StringResult`.
  - Connections: `ConnectEvent`, `DisconnectEvent`.
  - Health: `ApplicationLifecycleEvent` (STARTED and STOPPING), `RuntimeLifecycleEvent`, `HostMetrics`, `CacheEvictionEvent`.
  - Transport: `NativeWebRequestMetric` (no headers or bodies, but the path and query may be sensitive), `WebsocketTransportMetric`.
- **Disabling:**
  - Per consumer: `@Consumer(handlerInterceptors = DisableMetrics.class)`, or use `batchInterceptors`.
  - Client-wide: `disableMetrics = true`, plus `disableTrackingMetrics()`, `disableCacheEvictionMetrics()` and `disableApplicationLifecycleMetrics()` on the builder.
  - Temporarily: `AdhocDispatchInterceptor.runWithAdhocInterceptor(() -> {...}, (m, t, topic) -> null, MessageType.METRICS)`.

### Correlation data
Source: <https://fluxzero.io/docs/guides/messaging/correlation-data/>

- Every outgoing message carries metadata that links it to its application, handler and trigger.
- Custom trace context: `Metadata.empty().withTrace("operationId", "evening-routine")` adds `$trace.operationId`.
  - All `$trace.*` entries are copied onto follow-up messages.
  - Ordinary metadata is not.

| Key | Meaning |
|---|---|
| `$applicationId` / `$applicationVersion` | App identity and version (`fluxzero.application.version`) |
| `$taskId` | Platform task or pod (`FLUXZERO_TASK_ID`) |
| `$clientId` / `$clientName` | The SDK client instance and its logical name |
| `$consumer` / `$tracker` / `$handler` / `$invocation` | The active handling context |
| `$correlationId` | Index (or ID) of the message being handled; changes at each hop |
| `$traceId` | Constant across the whole chain |
| `$trigger` / `$triggerType` / `$triggerNamespace` | The triggering payload type, category and namespace |
| `$msDelay` | Age of the handled message in ms when correlation data was collected (not execution time) |

- `Fluxzero.currentCorrelationData()` returns the active context.
- To customize, use `builder.replaceCorrelationDataProvider(...)` and compose with the existing provider via `andThen(...)`.
- `$` is reserved for SDK keys. Keep sensitive data out of trace labels.

---

## 4.2 Modeling & persistence reference

### State in Fluxzero
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/state-in-fluxzero/>

| Mechanism | Use it for | Fluxzero owns |
|---|---|---|
| **Models and Graphs** | Domain objects and business facts | Identity, changes, relationships, history, atomic multi-Model operations |
| **Stateful handlers** | Progress across messages; current views assembled by handlers | Matching messages to stored instances, persisting the returned state |
| **Custom documents / key-value** | Application-owned read models, mappings, caches | Durable storage only; your handler owns the lifecycle |

- Prefer `DocumentStore` for new structured state, since it supports querying, indexing and updates.
- `KeyValueStore` is a legacy Runtime API for simple single-key values.

### Models and Graphs
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/models-and-graphs/>

- Each Model has its own identity, state, storage and lifecycle.
  - `@Parent` connects Models without merging their storage.
  - Updating or deleting a Task never rewrites its Project.
  - Changing the relation moves a Task without changing its identity.
- A **Graph** is a connected, lazily loaded view at one read boundary.
  - It is a view, not persisted state.
  - Use it when a read depends on related Models or on earlier state.

```java
Graph<Project> project = Fluxzero.loadGraph(projectId);
List<Task> tasks = project.childModels("tasks", Task.class);
Task task = Fluxzero.loadModel(taskId).get();
```
- An `AddTask.apply(Project project)` parameter makes the action depend on an existing Project.

**Choosing a boundary by lifecycle**

| Use | When |
|---|---|
| A separate Model | It has its own creation, changes, history, retention or deletion |
| A plain value object | It changes only as part of its owner |
| `@Member` | Its whole lifecycle deliberately belongs to one root |

Size and shape don't decide this. A small status can be its own Model.

### Loading models
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/entity-loading/>

**Basics**
- `Fluxzero.loadModel(new OrderId("order-123")).get()` loads by typed ID. `get()` returns `null` if absent.
- `Fluxzero.loadModel("order-123", Order.class)` loads by string ID plus class.
- Loading a Model never loads its parents or children.
- `Graph<Order> g = Fluxzero.loadGraph(orderId)`:
  - `g.get()`, `g.previous()`, `g.childModels("lines", LineItem.class)`, `g.isPresent()`.
  - `g.current()` opens a new current view.
  - Lazy; once a boundary is set, it stays fixed.
- Passing `false` (for example `loadCurrentGraph(graph.id(), Order.class, false)`, or the same on `loadModel`, `loadGraph`, `loadModels`) uses a complete repository key unchanged, with no affixes. Aliases still resolve.

**`@Alias`** (alternative identifiers)
- Example: `@Model record Customer(@EntityId CustomerId customerId, @Alias(prefix = "email:") String email, ...)`, loaded with `loadModel("email:info@example.com", Customer.class)`.
- Aliases are stored atomically with each transition.
- An old alias stops resolving after commit. Deletion removes aliases.
- Aliases are globally unique. A primary ID always wins over an equal alias.

**Parent-scoped IDs**
- `@EntityId(parentScoped = true) String lineId` together with `@Parent(value = Order.class, pathInParent = "lines") OrderId orderId`.
- Access it with `order.find("one", Line.class)` or `Fluxzero.loadGraph(orderId, Order.class, "one", Line.class)`.
- Not suitable for Models that move while keeping their identity.

**Event-sourced load path**
1. Cache.
2. Snapshot or checkpoint.
3. Only the missing suffix of the Model's own stream.
4. Replay through `@Apply`.

- Child Models have their own short streams.
- A Model operation with Graph dependencies pins its read boundary in storage, even when the root is cached, because a cached root can't prove its children are current.
- Cache entries never cross repository families or namespaces.

**DOCUMENT load path**
- `@Model(persistence = ModelPersistence.DOCUMENT)` loads from a synchronously maintained document.
- Events are still stored and published unless configured otherwise.
- Remote commits invalidate caches in other SDK instances.

**Injection in handlers**
- `@HandleEvent void on(ItemReserved e, Order order, Graph<Inventory> inv)` matches IDs in the event:
  1. A property named like the `@EntityId`.
  2. A unique typed `Id<Model>`.
  3. `@Association("sourceId")` (metadata is checked first unless `excludeMetadata = true`).
- A deleted target arrives as an empty Graph. A bare `T` parameter only matches when the Model exists.
- Ancestors are injected without their IDs: `inspect(InspectLine q, LineItem line, Order order, Customer customer)`. Use `@Association("billingLines")` to pick a path.
- `loadGraph` inside a handler inherits the message's boundary. Use `loadCurrentGraph` for current intent, for example after `sendCommandAndWait`, or in a scheduling reconciler.

**Complete graph changes**
- A single unqualified `Graph<T>` parameter (context parameters allowed in any order) receives every durable change under that root.
  - Each root is invoked once per substep.
  - Creation has no `previous()`. Deletion gives an empty current Graph and the full `previous()`. A move notifies both roots.
  - Cascaded deletions notify the child's handler through the original domain event.
- `@HandleNotification` works the same, without segmentation. One handler object can hold methods for several root types.
- Custom context parameters: override `ParameterResolver.isContextParameter(parameter)`.
- Exact `previous()` values require EVENT_SOURCED. Otherwise use strict mode (`graph.strict(true)` or `fluxzero.model.graph.strict=true`).

**Whole Graphs**
- `modelRepository().loadGraphAt(orderId, stateIndex)` loads a historical boundary and never overlays pending batch state.
- No default depth or count limit; pass `Graph.Options` to bound a load.
- Serializing a Graph includes only edges that have a `pathInParent`.
- `graph.withContext(value)` and `graph.context(Type.class)` share response-only context with `@GraphProperty` methods.
- Current loads inside a tracking batch include pending changes from earlier messages in the same segment.

### Temporal Graphs
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/temporal-graphs/>

**Navigation**

| Method | Use |
|---|---|
| `children(...)` | Immediate children |
| `descendants(...)` | Deeper traversal |
| `parent()`, `ancestorModel(T.class)` | Toward the root |
| `childModels(...)` | Values only |
| Explicit paths | Choose a branch |

- Collection parents (`Set<ProjectId>`) create one edge per ID. List order doesn't define child order.
- Untyped IDs use `@Parent(ParentModel.class)`; polymorphic IDs use `types`.

**Inspecting the triggering change**
- `get()`.
- `previousValue(t -> t)`.
- `hasChanged(Task::completed)`; an absent side counts as `null`.
- `ancestorModel(Project.class)`.

**Transitions as seen by a `Graph<Project>` handler**

| Transition | Current graph | Previous graph |
|---|---|---|
| Create root | Root exists | `null` |
| Add child | Includes the child | Doesn't include it |
| Update child | New value | Old value |
| Move child | Old root loses it; new root gains it | Each root keeps its own before-view |
| Delete root | Empty | The full pre-deletion Graph |

- To build an activity feed, diff `childModels("tasks", Task.class)` by ID between `previous()` and the current Graph.

**Revisions**
- `task.revisions().limit(20)` walks newest-first, excluding the empty pre-creation state.
- Deletion is a real revision whose `get()` returns `null`; don't filter out every null.
- Filter business changes with `revision.hasChanged(Task::version)`.
- Bound the traversal on interactive screens.
- A root's revisions don't list descendant changes; use the Graph-change handler for that.
- `previous()` returns `null` at creation.
- `atStateIndex(saved)` reconstructs a durable boundary.
- `playBackToCondition(...)` and `playBackToEvent(...)` return an `Optional`.

**Coordinates**

| Coordinate | Meaning |
|---|---|
| `stateIndex()` | The Graph's read boundary |
| `revisionStateIndex()` | When this node's revision became effective |
| `sequenceNumber()` | This Model's revision count |

These are different things, and a timestamp does not uniquely identify a commit.

**Current intent**
- Use `eventView.current()` or `loadCurrentGraph(id)`. Inside a mutation, they join the attempt's boundary.
- For invariants, use injected Models or Graphs, never search results or detached Graphs.

**Strictness**
- If a DOCUMENT-only historical version is unavailable, the view falls back to the current value of the same ID (`null` if deleted).
  - Relationships stay historical, and a node proven absent stays absent.
- `fluxzero.model.graph.strict=true` (`FLUXZERO_MODEL_GRAPH_STRICT`, default `false`) requires exact values.
  - Per view: `graph.strict(true|false)`; check with `isStrict()`.
  - Strictness propagates through navigation.
- A lenient view keeps its first fallback read, and values are not an atomic snapshot.
- Assertions, applies and replay are always strict.
- The fallback needs Runtime support for historical head-only reads.

**Retention**
- Exact history needs EVENT_SOURCED on every inspected node. A bigger cache can't replace it after a restart.
- Physical erasure, missing types, lost keys or pruned data make history unavailable.
- Test both cold and warm reconstruction across: create, add, change, move, delete.

### Updating models
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/updating-entities/>

**Basics**
- Send the command and Fluxzero loads the target, runs assertions and applies, and commits. No persistence code is needed.
- Example: `RenameProduct(ProductId, @NotBlank name)` has `@Apply Product apply(Product p)`, returning `p.withDetails(p.details().withName(name))`.
- Errors:
  - Business refusals: `FunctionalException` subclasses such as `IllegalCommandException` or `UnauthorizedException`. Don't use `IllegalStateException`, `IllegalArgumentException` or Kotlin `require`/`check`.
  - Malformed input: Bean Validation.
- `@Apply` can also be a receiver method on the Model. Register the Model class as a handler in that case.
- Run the pipeline explicitly with `Fluxzero.assertAndApply(command[, Metadata])`, called once, for a custom result.

**Create and delete**

| Apply shape | Requires |
|---|---|
| No current-state parameter (factory) | Target absent |
| Non-null current parameter | Target present |
| Nullable current parameter | Either (upsert) |

- A mismatch rejects with already-exists or not-found.
  - `disableCompatibilityCheck = true` suppresses the rejection, but a factory still never overwrites.
  - Configure with `fluxzero.assert.apply-compatibility` (`.exception.already-exists`, `.exception.not-found`).
- Returning `null` is a logical delete; the stream is kept. Returning `void` from a Model apply is not allowed.
- Physical deletion uses `ModelRepository.deleteModel(...)` with a confirmed plan.

**Other Models in an action**
- Any referenced Model can be injected into assertions, interceptors and applies. It is read-only unless returned.
- For two of the same type, use `@Association("sourceId")` and `@Association("destinationId")`.

**Alias targets**
- Enable with `fluxzero.model.aliasTargets=true`, or defaults ≥ `2026.10.18`.
- A property named like an `@Alias` field selects by alias.

| Selection | Result |
|---|---|
| All selectors find the same owner | Update, change aliases or delete |
| Some find one owner, others absent | Update; the final value must satisfy all selectors |
| All absent | Create, if valid |
| Different owners | Rejected as ambiguous |

- Upgrade all SDK readers first.

**Ancestors**
- Followed at the pinned boundary.
- Returning an ancestor from a singular apply makes it a write target.
- Ancestor selection uses the pre-apply Graph, so you can delete a child and update its parent atomically.
- Ambiguous ancestors must be qualified.
- A nullable read-only parameter accepts a null ID. Null is never a valid write identity.

**Multi-Model actions**
1. Batch-load targets and ancestors.
2. Run assertions and interceptors.
3. Stage changes.
4. Commit one package.
5. Publish the event once.

**Conflict policies**

| Policy | Behavior |
|---|---|
| `ACCEPT` | Rebase. Validates only apply dependencies and written targets. |
| `FAIL` | Roll back and report the conflict |
| `RETRY` | Full re-evaluation, bounded |
| `DEFAULT` | Inherit, otherwise `RETRY` |

- Configure via the builder, `fluxzero.model.conflictPolicy`, per `@Model`, or per `@Apply`.
- A concurrent first creation never rebases into an overwrite.

**Automatic Model routing**
- Enable with `fluxzero.model.automaticRouting=true`, or defaults ≥ `2026.09.10`.
- Single-apply commands are routed by target ID.
- Explicit `@RoutingKey` always wins, even when its value is absent.
- Routing reduces write contention; it does not remove conflicts.

**Commands in one tracking batch**
- Each command stays its own atomic action, but later commands in a segment read earlier staged changes.
- Overlapping chains wait for their predecessor and re-evaluate; independent ones run in parallel.
- Each command keeps its own outcome.
- `fluxzero.model.awaitAfterHandlerCommitsBeforeResults=false` lets results go out before deferred commits finish.

**`@InterceptApply`**

| Interceptor returns | Effect |
|---|---|
| The same payload | Its assertions and applies run |
| `null` | Nothing runs |
| A replacement | The replacement's checks and applies run |
| A list (split) | Each part runs in order and sees earlier parts |

- `@InterceptApply(assertCurrent = AssertCurrent.ENABLED | DISABLED | DEFAULT)`:
  - `DEFAULT` follows `fluxzero.interceptApply.assertCurrent`; enabled from defaults `2026.10.04`.
  - Enabled means immediate assertions run on the interceptor's own input, once.
- `afterHandler = true` assertions run against the complete final state of the whole operation. They must be pure.

**Composable assertions**
- An `@AssertLegal` method can return validator objects, which are checked recursively.
- Parameters resolve from the nearest validator that declares a reference.
- Before and after phases are kept. `@AssertLegal` fields delegate in both phases.
- Visits use object identity; nesting is capped at 256.

**Dynamic write targets**
- `@Apply List<Child> apply(Graph<Root> root)` returns updated children. Values must come from this evaluation's tracked Graph.
- `@InterceptApply List<Graph<Child>>` can use `graph.update(...)` and `graph.delete()`.
- An ordered list of commands commits atomically under one commit ID; cold replay rebuilds identical state.

**`Fluxzero.commit()`**
- Makes an already produced automatic commit durable early and returns its future.
- It never creates a second commit. Don't block on it inside `@Apply`.

### Model relations and embedded members
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/nested-entities/>

| Intent | Modeling |
|---|---|
| Store an ID with no Graph edge | Plain typed ID |
| Edge, cascade delete | `@Parent` (`deleteOnParentDeletion = true` by default) |
| Edge, no cascade | `@Parent(deleteOnParentDeletion = false)` |
| Also block parent deletion | `preventParentDeletion = true` |

- Example: `LineItem` has `@Parent(pathInParent = "lines") OrderId` and `@Parent(deleteOnParentDeletion = false) ProductId`.
- Concrete ID cycles are rejected. Recursive same-type relations such as `Folder` → `parentFolderId` are allowed.
- **Move:** return a copy with the new parent ID. Return `null` to detach. Neither parent is loaded. Historical loads show the relation as it was then.
- **Parent ID types:**
  - Typed `Id<Parent>`.
  - Untyped: `@Parent(value = Order.class, ...)`.
  - Polymorphic: `@Parent(types = {Project.class, Folder.class}) Id<?>`.
    - Serialized as `{"name":"project","id":"owner-a"}` for Model IDs, or `{"@class":"...","id":"..."}` otherwise.
    - Upgrade readers before writers.
- `pathInParent` is a durable placement contract. Nested paths use slashes (`"shipping/lines"`).
  - Without it, the edge is navigable but not in search or JSON documents.
- Search across relations:
  - `search(LineItem.class).whereAncestor(Customer.class, 2, 2, match("Acme", true, "name"))`.
  - `search(Order.class).whereDescendant(LineItem.class, 1, 1, match(...))`.
  - The integers are the min and max relation depth.
- Materialized root: `@GraphProjection(mode = ASYNC, collection = "order-graphs")`.
  - Written with a `(configurationVersion, stateIndex)` fence; a move updates both roots.
  - `AWAIT` blocks the command result until the projection catches up. An operation-level AWAIT override is preferred.
- **`@Member`:**
  - Not deprecated. Members share the owner's stream, snapshots and document.
  - Choose it by lifecycle ownership.
  - Converting a member into a Model requires a data migration.
- **Deletion:**
  - Cascades follow each edge's own policy.
  - A child moved away in the same commit survives. Delete-and-recreate in the same commit doesn't cascade.
  - Hard delete removes the stream, cache, snapshots and document. Descendant hard deletes need `planDeletion(id, ModelDeletionCascade.DESCENDANTS)`.
  - Detached descendants keep former-parent lineage for GDPR cleanup. Published audit events fall outside hard delete.

### Model persistence
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/model-persistence/>

**Persistence sets**

| Set | Behavior |
|---|---|
| `{EVENT_SOURCED}` (default) | Rebuilt from events |
| `{DOCUMENT}` | One internal current document, written synchronously with the commit. No history. |
| `{EVENT_SOURCED, DOCUMENT}` | Both |

- An empty or duplicate set is rejected.
- `ignoreUnknownEvents = true` (event-sourced only) skips events with no apply. It changes state, so treat it as a deliberate decision.

**Current state without replay**
- `Fluxzero.loadCurrentModelState(id, Account.class)` returns `ModelState<T>`: the value, the exact ID, the durable head and the verified boundary.
  - Needs a maintained canonical document and a trusted SHA-256 proof.
  - Retries at most 8 times and fails rather than returning stale data.
  - Read-only; it never joins a commit readset.

**Cross-application navigation**
- `children(path[, modelName], knownOnly)`, `namedChildren(name, false)` and `descendants` count locally unknown types too.
- `modelName()` gives the logical name. `knownType()` is empty for unknown nodes.
- `selectPaths(...)` filters before reconstruction.
- Lazy alias and root discovery work from head metadata without replay.

**Event publication**

| `eventPublication` | Effect |
|---|---|
| `ALWAYS` | Always store and publish |
| `IF_MODIFIED` | Suppress when the Model is unchanged |
| `NEVER` | No Model event; a DOCUMENT can still commit |
| `DEFAULT` | Inherit |

| `publicationStrategy` | Effect |
|---|---|
| `STORE_AND_PUBLISH` | In the Model stream and published globally once |
| `STORE_ONLY` | In the stream, not published globally |
| `PUBLISH_ONLY` | Published, not in the stream |
| `DEFAULT` | Inherit |

- Event-sourced Models must store their events, so `PUBLISH_ONLY` and `NEVER` are rejected for them.
- The event envelope gets its own ID and creation time.
  - Retries keep both.
  - `$modelCommitId` metadata identifies the commit.
- Multi-target events use lightweight memberships and may share one payload.

**Commit package**
- Contents: events, memberships, read boundary, documents, due snapshots, relation deltas and policies.
- The Runtime assigns a monotone `stateIndex` and commits everything in one JDBC transaction.
- Documents and snapshots complete before the result is released. Graph projections may lag unless AWAIT.
- `commitPolicy` (`ModelCommitPolicy`) controls when commits start. Keep `DEFAULT`.

**Search**
- Example: `@Model(searchable = true, searchSettings = @SearchSettings(collection = "line-items"))`.
- `search(T)` returns node values and can filter by ancestor ID even if the ancestor isn't searchable.
- `searchGraph(T)` composes live (`NONE`) or reads the stored Graph (`ASYNC`/`AWAIT`).

**Caching**
- A shared cache plus an action-local cache. One namespace-level long poll invalidates changed IDs.
- `withModelCache(cache)` or `disableAutomaticModelCaching()` on the builder; `cached = false` per Model.
- `cachingDepth` is a performance setting, not retention.

**Snapshots**
- Off by default (`snapshotPeriod = 0`). Add them only when replay cost is measured.
- Example: `@Model(snapshotPeriod = 1000, maxSnapshotCount = 2, checkpointPeriod = 100)`.
- `maxSnapshotCount`: positive keeps that many, `0` keeps 1, `-1` keeps all (needs an upgraded Runtime).
- Snapshots never overwrite a higher `stateIndex`. Trimming is not a commit invariant.
- A cold alias read can fetch head, snapshot and the first event page in one request.
- Replay reads the Model's own stream in bounded pages.

**Cascading rules**
- `@AssertLegal(cascade = true)` on a parent applies to all descendants.
  - Filter with `allowedClasses` or a typed payload parameter.
  - Opt out with `@Parent(validateAncestors = false)` or `@Apply(ancestorValidation = AncestorValidation.DISABLED)`.
- Rules run on pre-update state; `afterHandler = true` runs on the final state. Both the old and new routes are checked.
- A configured `ACCEPT` becomes `FAIL` while cascading validation applies.
- Every writer must deploy the rule. If there are no rules, there is no extra cost.

**Conflict policy (detail)**
- `fluxzero.model.conflictPolicy` / `FLUXZERO_MODEL_CONFLICT_POLICY`. `DEFAULT` = `RETRY`.
- An injected Graph protects the values and relationship collections you inspect, including empty ones. That's how capacity checks stay safe.
- Physical erasure invalidates older Graph reads namespace-wide.
- Custom repositories must use `Graphs.compose` to support transactional navigation.

**`@Parent` shorthands**
- Example: `@Parent(required = true, maxChildren = 100, preventParentDeletion = true)`.
  - `maxChildren`: `-1` means unlimited, `0` means no growth.
- Failures throw `IllegalCommandException`. Use `ModelErrors.parentRequired`, `parentNotFound`, `maxChildrenExceeded` and `parentDeletionBlocked` in test assertions.

**Discovery and packaging**
- Run the SDK annotation processor (kapt in Kotlin) in every Model module. It generates the Model index.
- For shaded or fat JARs, add Maven Shade's `AppendingTransformer` for `META-INF/io.fluxzero.sdk.modeling.Model`.

**Performance**
- Commits are ID-oriented and batched by segment; ordinary loads do no relationship work.
- Give independently changing or searchable children their own Models.

### Model state and protection boundaries
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/model-state-boundaries/>

| Need | Capability | Not covered |
|---|---|---|
| Atomic related changes | One action with multiple `@Apply` targets | External calls, schedules, other repositories |
| Load by identity | `loadModel(id)` | Knowing an ID is not authorization |
| Current state without events | `DOCUMENT` + `eventPublication = NEVER` | No history; doesn't suppress command or web logs |
| Internal DOCUMENT state | No effective searchability | Search needs searchability |
| Concurrency checks | `RETRY`/`FAIL` + injected Models | Separate read-then-write |
| Remove current state | `@Apply` returns `null` | History is not erased |
| Erase storage | `deleteModel(...)` / deletion plan | Event logs, copies, backups |

**Example invariant on an independent Model**
- `Reserve(reservationId, productId)`:
  - `@AssertLegal check(@Nullable Product p)` rejects a missing or inactive product.
  - `@Apply(conflictPolicy = ModelConflictPolicy.RETRY)` creates the Reservation.
- If the Product changes before commit, RETRY re-checks it. A rejecting assertion is final.
- Never trust caller-owned metadata in `@Association` for authorization.

**Read boundaries inside a mutation**

| Read | Protected? |
|---|---|
| Injected `T` / `Graph<T>` | Yes (attempt snapshot and dependencies) |
| `loadGraph` / `loadCurrentGraph` / `current()` on the same repository | Yes (joins the readset) |
| `loadModel` in a helper | No new dependency; inject instead |
| Search / `loadCurrentModelState` | No |
| `previous()` / `at…` | No (historical) |

**Other contracts**
- DOCUMENT-only mutations defer or perform eager head reads. A lost pinned version during deletion prep raises a terminal `ModelCommitConflictException`; `getReadConflict()` gives details.
- SDK rc.13 and earlier don't attach manual Graph loads to the readset; use injection there.
- Not searchable ≠ private. Enforce access in handlers. `@ProtectData` doesn't carry over into copied Model state.
- No general encryption-at-rest or KMS guarantees.
- Erasure:
  - Removes stream, snapshots, sources, projections and cache, and fences delayed writes.
  - The global log and external copies are untouched.
  - Before trusting ancestor projections, wait for them to reach the deletion's state index.
- During reconstruction, Graph reads in `@Apply` see only earlier substeps.

### Stateful handlers
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/stateful-handlers/>

- `@Stateful` classes hold workflow memory (Models hold business facts). They are stored in the document store or a custom `HandlerRepository`.
- Messages are matched by `@Association` fields or methods, which are indexed. One message can match many instances.

**Lifecycle**
- Created by a static `@Handle…` factory or constructor when nothing matches.
- Updated by returning a new instance.
- Deleted by returning `null` or an empty collection.

**Return-value semantics**

| Return | Effect |
|---|---|
| Same-type instance | Replaces the stored state. A different `@EntityId` stores the new ID and deletes the old one. |
| Collection | Stores each item (fan-out). The current instance is deleted if its ID is missing. |
| Empty collection or `null` | Deletes the current instance |
| `void` or another type | No change. For example, `@HandleSchedule Duration on(CheckStatus s)` sets the next delay. |

**`@Member` children**
- Members of a `@Stateful` have their own handlers and associations; the parent is stored.
- `@Association(always = true)` fans a message out to all matching parents.
- The parent's own mutation runs first, then the members.
- Map-backed members are keyed by `@EntityId` or `@Member(idProperty = "...")`.

**Other**
- `commitInBatch = true` defers persistence to the end of the batch. Later messages in the batch still see the changes. Higher throughput.
- Automatically `@Searchable`: custom `collection`, `timestampPath`, `endPath`.
- Use cases: sagas, pollers, reminders, API orchestration, process managers.
- For external effects:
  1. Persist the pending action and its idempotency key.
  2. Reload current intent before acting.
  3. Record the acknowledgement before removing the pending action.
- `@HandleEvent` reacts to every transition. `@HandleDocument` may skip intermediate versions.
- Invocation flow: check associations → look up → create via factory if none → load → invoke → apply the return value. Runs behind a tracker.

### Document indexing and search
Source: <https://fluxzero.io/docs/guides/modeling-and-persistence/document-index-and-search/>

**Indexing**
- `Fluxzero.index(obj)` or `Fluxzero.index(obj, "collection")`.
  - The ID comes from `@EntityId`; otherwise it is random.
  - `@Searchable(collection, timestampPath, endPath)`.
- Auto-indexed types: `@Aggregate(searchable = true)`, `@Stateful`, `@Searchable` POJOs. The default collection is the simple class name.

**Queries**
- `Fluxzero.search("users").match("admin", "profile/role").inLast(Duration.ofDays(30)).sortBy("profile/lastLogin", true).fetch(100)`.
- Paths accept `.` or `/`.
- After `sendCommandAndWait` on a searchable aggregate, a direct document query sees the committed state. Projections are eventually consistent.

| Constraint | Purpose |
|---|---|
| `lookAhead(term, paths…)` | Search-as-you-type |
| `query("*text & (cat* \| hat)", paths…)` | Full-text with operators |
| `match(value, path)` | Equality |
| `matchFacet(name, value)` | Faster equality on `@Facet` fields |
| `between(min, max, path)` | Numeric or time range |
| `since`, `before`, `inLast` | Time filters |
| `anyExist(...)` | Any listed field present |
| `not`, `all`, `any` | Composition |

**Async methods** (return the future; don't `join()` in handlers)
- `fetchAsync(n[, type])`, `countAsync()`, `aggregateAsync(fields)`, `groupBy(paths).aggregateAsync(...)`, `facetStatsAsync()`.

**Facets**
- `@Facet` fields support `facetStats()`, which gives a count per value per facet.

**Indexing control**
- `@SearchExclude` on a field or type: stored, but not matchable.
- `@SearchInclude` (= `@SearchExclude(false)`) overrides an inherited exclusion.
- `transient` or `@JsonIgnore` keeps a field out of storage entirely.

**`@Sortable`** (pre-indexed for in-store range and sort)
- `@Sortable("releaseDate") Instant publishedAt` sets an alternate indexed name.
- Example: `.between(10, 100, "price").sortBy("releaseDate")`.
- Encodings:
  - Numbers: padded base-10.
  - Instants: ISO-8601.
  - Strings: lowercased, trimmed, diacritics removed.
- Collections index their maximum element; maps use `key/property` paths.
- No retroactive indexing; reindex with `@HandleDocument` and a bumped `@Revision`.
- Without `@Sortable`, sorting and filtering happen in memory, which is slow.

**Output and bulk operations**
- `exclude("profile.ssn")` or `includeOnly(...)` controls returned fields.
- `.stream()` for large result sets.
- `search("expiredTokens").before(Instant.now()).delete()` deletes matches.

---

## 4.3 Data control & serialization reference

### Protecting sensitive data
Source: <https://fluxzero.io/docs/guides/data-control-and-serialization/protecting-sensitive-data/>

- `@ProtectData` (`io.fluxzero.sdk.publishing.dataprotection`), **fields only**:
  - The value is stored separately at publish time, with a reference left in the message, and restored for handlers.
  - A local-only, unlogged invocation passes the value in memory.
- `@DropProtectedData` marks a handler as the final use. Values are restored for that call, then deleted irreversibly. Don't rely on them for retries.
- Model events are protected too. Replay restores retained values, and erased fields become `null`.
- Custom serializers must map property paths correctly.

**Missing data policy** (`MissingProtectedDataPolicy`)

| Policy | Behavior |
|---|---|
| `HANDLE` (default) | Invoke with `null` fields |
| `WARN` | Log, then invoke with `null` |
| `SKIP` | Skip this invocation; emits `IgnoreMessageEvent` with reason `missingProtectedData` |
| `FAIL` | Use the consumer's error handling |
| `DEFAULT` | Inherit |

- Set it on the handler (`@HandleEvent(onMissingProtectedData = SKIP)`), the consumer, the builder (`onMissingProtectedData(...)`), or the property `fluxzero.dataProtection.onMissingProtectedData` (`FLUXZERO_DATA_PROTECTION_ON_MISSING_PROTECTED_DATA`), in that order of precedence.
- Nested structures have limited support; annotate each sensitive field explicitly.

### Upcasting and downcasting
Source: <https://fluxzero.io/docs/guides/data-control-and-serialization/upcasting-and-downcasting/>

- Mark the class with `@Revision(n)`. Casters run in your app, before deserialization; stored data is never changed.
- `@Upcast(type = "fqn", revision = N) ObjectNode up(ObjectNode json)`, for example `json.set("userId", json.remove("id"))`.
  - Works on top-level objects only: payloads, documents, snapshots and KV entries.
  - A nested change needs an upcaster per affected top-level type. The exception is materialized Graphs, where each node is upcast separately.
- **Supported variants:**
  - Rename the type: return `Data<ObjectNode>` using `data.withType("new.fqn")`.
  - Change metadata: inject `Metadata` and return it. This replaces the metadata; use `@Nullable` when documents are also handled.
  - Drop: return `null` or `void`.
  - Split: return `Stream<Data<ObjectNode>>`.
- Registration batches are atomic; a conflict means none of the batch is activated.
- **Type aliases** (pure renames):
  - `fluxzero.serialization.typeAliases=old.Type=new.Type,old.pkg.*=new.pkg.*` (`FLUXZERO_SERIALIZATION_TYPE_ALIASES`).
  - Or programmatically with `addTypeAlias` and `addPackageAlias`.
  - Exact beats package; the longest prefix wins; programmatic beats properties.
  - **Upcasters run before aliases**, so an upcaster must target the old type name.
- **Flow:** raw (type, revision) → matching upcasters, repeated → aliases → deserialize. The app only ever sees the latest revision.
- **Testing:** JSON with `"@class"` and `"@revision"`, then `fixture.registerCasters(...).whenUpcasting("/users/create-user-revision-0.json").expectResult(...)`.
- **Downcasting:** `@Downcast(type, revision)`. Call `Fluxzero.downcast(obj, revision)` to get the old serialized form.
- **`@RegisterType`** (package or type, generated by the annotation processor) lets producers send simple names (`CreateUser`) or suffixes (`user.CreateUser`). Names must be unique. Merge the indexes in uber-JARs.
- Registration:
  - Spring beans with `@Upcast` or `@Downcast` are picked up automatically.
  - Otherwise call `serializer.registerCasters(...)`.
  - In fixtures use `registerCasters(...)`, because `TestFixture.create(caster)` registers it as a handler.
- Best practices:
  - Go one revision at a time.
  - Keep casters deterministic.
  - Use `ObjectNode` for structural changes, `Data` when the type changes, and aliases for pure renames.

### User-based content filtering
Source: <https://fluxzero.io/docs/guides/data-control-and-serialization/user-based-content-filtering/>

```java
@FilterContent
OrderView filter(User user) {
    return user != null && user.hasRole("admin") ? this : new OrderView(orderId, null);
}
// manual: Fluxzero.filterContent(order, currentUser)
```
- Filtering recurses into fields and nested objects. A nested `null` removes the list item or map entry.
- Filter methods can also take the root object: `LineItem filter(User user, Order root)`.
- **Automatic filtering** applies to request results when `@FilterContent` is also on the handler method, class or package.
- A `CompletableFuture` is filtered after it completes, with the original viewer. Failed futures are not filtered.
- Filter just before data reaches users. Internal Models stay complete.

---

## 4.4 Configuration reference

### Configuring Fluxzero (builder)
Source: <https://fluxzero.io/docs/guides/configuration/configuring-fluxzero/>

- **Spring:** the starter builds the instance. Customize it with a `@Component` implementing `FluxzeroCustomizer.customize(FluxzeroBuilder)`.
- **Outside Spring:** `DefaultFluxzero.builder()...build(client)`, using a `WebSocketClient` or `LocalClient`. `makeApplicationInstance(true)` makes `Fluxzero.get()` available globally.

| Area | Builder methods |
|---|---|
| Consumers | `configureDefaultConsumer(MessageType, UnaryOperator<ConsumerConfiguration>)`, `addConsumerConfiguration(...)`, `forwardWebRequestsToLocalServer(...)` |
| Interceptors | `addHandlerInterceptor`, `addBatchInterceptor`, `addDispatchInterceptor`, `replaceMessageRoutingInterceptor`, `addHandlerDecorator` |
| Identity and correlation | `replaceIdentityProvider` (default: first ServiceLoader provider, otherwise `UuidFactory`), `replaceCorrelationDataProvider`, `registerUserProvider` |
| Caching | `replaceCache`, `replaceRelationshipsCache`, `replaceSnapshotSerializer`, `withModelCache`, `disableAutomaticModelCaching` |
| Serialization | `replaceSerializer`, `replaceDocumentSerializer` (owns DOCUMENT Model documents; can encrypt, compress or sign), `addTypeAlias(es)`, `addPackageAlias(es)` |
| Handlers | `addParameterResolver`, `replaceValidator`, `replaceDefaultResponseMapper`, `replaceWebResponseMapper` |
| Properties | `addPropertySource`, `replacePropertySource` |
| Scheduling | `replaceTaskScheduler` (owned by and closed with the instance) |
| Data protection | `onMissingProtectedData(...)` |
| Metrics | `enableHostMetrics()` |

**Toggles**
- `disableErrorReporting`
- `disableShutdownHook`
- `disableMessageCorrelation`
- `disablePayloadValidation`
- `disableDataProtection`
- `disableScheduledCommandHandler`
- `disableTrackingMetrics`
- `disableCacheEvictionMetrics`
- `disableWebResponseCompression`
- `disableAdhocDispatchInterceptor`

**Spring auto-configuration**
- Picks up `Serializer`, `Cache`, `Client`, `UserProvider` and `WebResponseMapper` beans.
- Registers casters automatically, and registers handlers after context refresh.
- `@TrackSelf`, `@Stateful` and `@SocketEndpoint` beans are wired automatically.
- If no `Client` is configured, it tries a `WebSocketClient` from properties, then falls back to `LocalClient`.
- Spring without Boot needs `@Import(FluxzeroSpringConfig.class)`.
- Injectable beans: `Fluxzero`, `CommandGateway`, `EventGateway`, `QueryGateway`, `MetricsGateway`, `ErrorGateway`, `ResultGateway`, `MessageScheduler`, `DocumentStore`, `KeyValueStore`.
  - Prefer static `Fluxzero.*` calls: less boilerplate, and the same code works in tests.

**Shutdown grace**
- `fluxzero.shutdown.requestTimeoutMillis` (`FLUXZERO_SHUTDOWN_REQUEST_TIMEOUT_MILLIS`).
- Default 2000 ms per component; `0` skips it.

### Configuring the WebSocket client
Source: <https://fluxzero.io/docs/guides/configuration/configuring-websocket-client/>

```java
var client = WebSocketClient.newInstance(WebSocketClient.ClientConfig.builder()
    .runtimeBaseUrl("wss://my.flux.host").name("my-service").build());
Fluxzero fz = DefaultFluxzero.builder().makeApplicationInstance(true).build(client);
```

| Setting | Default |
|---|---|
| `runtimeBaseUrl` | `FLUXZERO_BASE_URL` / `FLUX_BASE_URL` |
| `name` | `FLUXZERO_APPLICATION_NAME` |
| `applicationId` | `FLUXZERO_APPLICATION_ID` |
| `id` | `FLUXZERO_CLIENT_ID`; otherwise a UUID, prefixed with the task ID when set |
| `supportedCompressionAlgorithms` | ZSTD only |
| `pingDelay` / `pingTimeout` | 10 s / 15 s |
| `maxConcurrentRuntimeWebSocketMessages` | 3 |
| `maxRetainedRuntimeWebSocketMessages` | 128 |
| `maxRetainedRuntimeWebSocketBytes` | 64 MiB |
| `maxConcurrentRuntimeResultCompletions` | 8 |
| `runtimeIngressStallCloseTimeout` | Disabled |
| `aggregateHistoryMaxFetchBytes` | Count-only; 100 MiB from defaults `2026.09.10` |
| `disableMetrics` | `false` |
| `typeFilter` | `null` |

- `ClientConfig` is immutable; derive variants with `toBuilder()`.
- **Task and client IDs:**
  - `FLUXZERO_TASK_ID` identifies the pod. It prefixes the generated client ID and is published as `$taskId`.
  - The client ID is stable across reconnects.
  - Set `FLUXZERO_CLIENT_ID` only if you can guarantee it is unique.
- **Reconnect backoff:**
  - Enable with `fluxzero.websocket.reconnectBackoff.enabled=true`, or defaults ≥ `2026.09.09`.
  - Equal jitter over 1, 2, 4, 8, 16 s. The default is a fixed 1 s retry.
- **Ingress flow control:**
  - Properties: `fluxzero.runtime.ingress.maxConcurrency`, `.maxRetainedMessages`, `.maxRetainedBytes`, `.maxCompletionConcurrency`, `.stallCloseTimeout` (for example `PT30S`).
  - When full, the connection stops receiving. The byte limit governs admission; it is not a maximum response size.
- **Stalls:**
  - Diagnose with `fluxzero.websocket.transportMetrics.enabled=true`. A stall means retained work with no progress; idle is not a stall.
  - For manual lifecycles, call `Client.prepareShutdown()` and then `shutDown()`.
- **Sessions:**
  - `eventSourcingSessions(2)`, `searchSessions(3)`, `gatewaySessions(Map.of(COMMAND, 2, EVENT, 1))`.
  - `trackingConfigs(Map.of(EVENT, TrackingClientConfig.builder().sessions(2).cacheSize(1000).build()))` buffers messages per topic for high-fanout tracking.
- `LocalClient.newInstance()` simulates the whole platform in memory, for tests and local development.

### Application properties
Source: <https://fluxzero.io/docs/guides/configuration/application-properties/>

**API**
- `ApplicationProperties.getProperty`, `getIntegerProperty("page.size", 20)`, `getBooleanProperty`, `requireProperty`.

**Precedence** (highest first)
1. Environment variables (`PAGE_SIZE=50` overrides `page.size`).
2. System properties (`-D`).
3. `FLUXZERO_CONFIG_LOCATIONS` files.
4. `application-<ENVIRONMENT>.properties` (`ENVIRONMENT=dev`).
5. `application.properties`, merged across the classpath. Duplicate keys with different values produce a warning.
6. SDK defaults (`fluxzero.properties` / `fluxzero.json`).
7. Spring `Environment`, as a fallback.

- Conventional environment-variable names resolve to dotted properties, for example `FLUXZERO_AUTH_OIDC_LOGIN_STATE_SECRET` → `fluxzero.auth.oidc.login-state-secret`. If both forms are in the same source, the exact name wins.

**Versioned defaults: `fluxzero.defaults.version=yyyy.MM.dd`**
- Absent means compatibility behavior.
- Each version includes all earlier changes, and an explicit feature property always wins.

| Version ≥ | Equivalent property | Change |
|---|---|---|
| 2026.05.20 | `fluxzero.tracking.unconfiguredHandlerConsumerMode=perHandler` | One consumer per unconfigured handler |
| 2026.05.21 | `fluxzero.scheduling.periodic.useDefaultInitialDelay=true` | `@Periodic` first runs after its delay or at the next cron match |
| 2026.07.27 | `…unconfiguredHandlerConsumerMode=perPackage` | One consumer per package and message type |
| 2026.08.04 | `fluxzero.auth.useUserIdMetadata=true` | User ID in metadata, resolved via `getUserById` (from the RBAC page) |
| 2026.09.09 | `fluxzero.websocket.reconnectBackoff.enabled=true` | Jittered exponential reconnect |
| 2026.09.10 | `fluxzero.eventsourcing.maxFetchBytes=104857600`; `fluxzero.model.automaticRouting=true` | 100 MiB history pages; automatic Model routing |
| 2026.10.04 | `fluxzero.interceptApply.assertCurrent=true` | Check interceptor inputs |
| 2026.10.09 | `fluxzero.search.collectionValues=true` | Annotation-independent range and sort. Upgrade the Runtime first. |
| 2026.10.18 | `fluxzero.model.aliasTargets=true` | Commands can select Models by alias |

- Independent of the defaults version:
  - `fluxzero.model.graph.strict` (default `false`).
  - `fluxzero.proxy.http2MaxResponseHeaderSize` (16 KiB).
  - Delivery guarantees and conflict policy.

**Encrypted values**
1. `DefaultEncryption.generateNewEncryptionKey()` → `ChaCha20|…`. Set it as `ENCRYPTION_KEY`.
2. `ApplicationProperties.encryptValue("secret")` → `encrypted|ChaCha20|…`.
3. Put it in config (`google.apikey=encrypted|ChaCha20|…`). `getProperty` decrypts transparently.

- The Fluxzero platform auto-provisions a key per cluster; you can override it in the dashboard. Keep keys in a secret manager.

**Tests**
- Use `src/test/resources/application.properties`, `-D` overrides, or `TestFixture...withProperty("k", "v")`.

**Other properties mentioned across the guides**
- `fluxzero.publishing.defaultGuarantee`
- `fluxzero.tracking.consumerFiltering`, `fluxzero.tracking.maxWaitDuration`
- `fluxzero.runtime.messageEnvelopeVersion`, `fluxzero.runtime.consumerFiltering`
- `fluxzero.model.conflictPolicy`, `fluxzero.model.maxConflictRetries`, `fluxzero.model.graphProjectionCompletion`, `fluxzero.model.awaitAfterHandlerCommitsBeforeResults`
- `fluxzero.assert.apply-compatibility`
- `fluxzero.dataProtection.onMissingProtectedData`
- `fluxzero.serialization.typeAliases`
- `fluxzero.application.version`
- `fluxzero.web.native.maxConcurrentPerOrigin`, `fluxzero.proxy.forward.maxConcurrentPerOrigin`
- `fluxzero.proxy.responseHeaderBufferSize`, `fluxzero.proxy.useOutputDirectByteBuffers`
- `fluxzero.websocket.transportMetrics.enabled`
- `fluxzero.runtime.ingress.*`
- `fluxzero.shutdown.requestTimeoutMillis`
- `fluxzero.test.sync`
- `FLUXZERO_NAMESPACE`, `FLUXZERO_TASK_ID`, `FLUXZERO_CLIENT_ID`, `FLUXZERO_BASE_URL`, `FLUXZERO_APPLICATION_NAME`

---

## 4.5 Error codes
Source: <https://fluxzero.io/docs/errors/>

- Format: `FZ-SDK-NNNN`.
- Each exception carries the code, an explanation and context values.
- Codes are never renumbered, renamed or reused.
- The full explanation is logged for the first 100 occurrences per JVM; after that only a compact line is logged.

| Code | Meaning | Typical causes | Remedy |
|---|---|---|---|
| FZ-SDK-0001 | No Fluxzero instance available | Static helpers or `Fluxzero.get()` used outside `fluxzero.apply(...)`; used at startup before `applicationInstance` is set; tests without `TestFixture` | Run inside `apply`; set the application instance; use `TestFixture.create`/`createAsync` |
| FZ-SDK-0002 | Request timed out waiting for a result | No matching handler is running; handler is passive or returns nothing; namespace, topic, routing or consumer mismatch; auth filtering | Register and start a matching handler; check routing, passive flags and auth; use fire-and-forget if no result is expected |
| FZ-SDK-0003 | Handler invocation failed | Exception in the handler or an interceptor; wrong parameter or payload assumptions | Inspect the cause; use a `FunctionalException` for business rejections |
| FZ-SDK-0004 | Response dispatch failed | Response not serializable; interceptor error; result channel or client unavailable | Check serializability, interceptors and connectivity |
| FZ-SDK-0005 | Thread interrupted while waiting | Cancellation, shutdown, or an app-level timeout | Decide whether that is expected; retry at the boundary if safe |
| FZ-SDK-0006 | Message dispatch failed | Interceptor error, serialization failure, client closed, wrong namespace or topic | Check interceptors, serializer and connectivity |
| FZ-SDK-0007 | No `UserProvider` configured | `Message.addUser(...)` without a provider | Configure a provider, or set `UserProvider.defaultUserProvider` |
| FZ-SDK-0008 | Invalid consumer configuration | Duplicate names; handler registered after tracking started; no matching consumer; `Fluxzero.tracking(...)` for an untracked type | Use unique names; register handlers early; add a `@Consumer` or `ConsumerConfiguration` |
| FZ-SDK-0009 | Invalid periodic schedule | `@Periodic` with no cron and no positive delay; `schedulePeriodic` on a type without `@Periodic` | Add a cron or delay; use `@Periodic(cron = Periodic.DISABLED)` to disable; use `schedule(...)` for computed deadlines |
| FZ-SDK-0010 | Tracking failed at runtime | Can't store position; client or store unavailable; interrupted; max-index maintenance check failed | Fix the cause, then restart the tracker |

---

*End of extract. Every page listed under "Developer Guides" on fluxzero.io was covered. The only gap is the tail of the very long Fluxzero 2.0 deep-dive page (see 1.4).*
