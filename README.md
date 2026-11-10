# Meepleverse

This repository contains the Meepleverse demo shop and backend used in the **2-day in-person Flux Advanced Training**.

## SDK 1.x course baseline

The refreshed course uses **SDK 1.292.49**, pinned through `fluxzero.version` in `pom.xml`, and Java 25.
The build uses Spring Boot 4.1.2 and Maven 3.10.0 (via Maven Wrapper 3.3.4).
Spring Boot's version is independent of the Fluxzero SDK major version: this remains an SDK 1.x course.
Shared Jackson 2, JUnit, Logback and Jetty versions are aligned with the Fluxzero BOM in `pom.xml`.
Dependencies are resolved from the public Fluxzero Packages Maven repository and Maven Central.
Keep this course on SDK 1.x when changing examples or consulting documentation.

The existing unit tests use in-memory fixtures and mock the external payment, supplier and shipping services.
They can be run without Docker using the Maven wrapper shown below.

For local development with the Fluxzero CLI, run `fz dev --background` from this directory.
Use Dev Server **1.21.0 or newer** for `runtime.env` support. Start without an older `--dev-server-version` override.

Before starting day 2, update this checkout and restart the Dev Server:

```sh
fz dev stop
fz dev --background
```

The same commands work in Windows PowerShell, Command Prompt, macOS and Linux.
Stopping clears the in-memory course data. Place new orders and wait for shipments before starting exercise 3.
The repository configures two days of Test Server log retention in `.fluxzero/dev.yaml`, so replay and document
migration still work after explanations and breaks. Application reloads retain that data; stopping the entire
Dev Server clears it. Auditlog retention is separate. The startup link opens Devboard, with App preview for the shop.
The Dev Server selects the matching SDK 1.x Test Server and Proxy, compiles the applications and manages their tests.
The application initializes the catalog at startup. The Dev Server starts the local IDP from Fluxzero IDP test-support. There is no external account or separate IDP installation.
The configuration starts Meepleverse and the bundled PSP, supplier and shipper JARs on the same local runtime.
Use the proxy address reported by `fz dev status --json` for the existing `/catalog`, `/users` and `/orders` routes.
Run `fz dev stop` to stop this local environment.

The Docker Compose configurations below are the historical course stack, still pinned to runtime/proxy 1.56.0.
They require separate qualification before use with the refreshed course. Do not start both runtime stacks for one exercise.

## Introduction

In this training you’ll step into the world of **Meepleverse**, an e-commerce webapp for board games.

Meepleverse began as a small side project: the owners of a local board game shop hacked together a site over a weekend
so their loyal customers wouldn’t have to bike across town to pick up games.

What started as a simple service for a few locals quickly exploded in popularity. Gamers from all over the world
discovered Meepleverse — and demand skyrocketed.

With success came chaos: performance bottlenecks, broken flows, and mysterious bugs. The overwhelmed store owners have
now called on you, a seasoned Flux practitioner, to help them debug, tune, and stabilize their platform.

Your mission: **make Meepleverse scale** while keeping the orders flowing.

## What you’ll learn

Over the course of two days, you will:

- Explore how Flux works under-the-hood
- Troubleshoot and fix production issues
- Repair errors retroactively in running systems
- Evolve applications while they are in production
- Discuss custom topics brought in by participants
- Review selected SDK 1.x improvements relevant to the exercises

## Updating a preparation checkout

Before the training, public `main` contains only the environment setup check.
When your trainers publish the course application, stop Dev Server and run
`git pull --ff-only` on that unchanged `main`. Then create `my/exercise-01` and
start Dev Server again. Reload the Maven project in your IDE if prompted.
The preparation guide includes the complete commands.

## Setting up

For the November 2026 SDK 1.x course, follow the
[participant preparation guide](https://training.fluxzero.io/advanced/preparation/).
It covers Git, Fluxzero CLI installation on Windows/macOS/Linux, Java setup, IDE setup
and the readiness check. Start from the course repository on `main`:

```text
git clone https://github.com/fluxzero-io/meepleverse.git
cd meepleverse
git switch -c my/exercise-01
```

Work on your own local branch. After each exercise the trainers publish one possible solution. For example, after exercise 1, save your work and compare it with the published checkpoint:

```text
git add -p
git status
git commit -m "My exercise 1"
git fetch origin
git diff HEAD origin/solution-exercise-01
```

Stage any new exercise files by name before committing. If you have no changes, skip the commit. To start exercise 2 from the trainer checkpoint, use `git switch -c my/exercise-02 origin/solution-exercise-01` after saving your work. Your first branch stays available. You can also continue on your own branch. The same steps work in PowerShell, Terminal and Git Bash.

A fork is optional. Open this folder in your Java IDE with JDK 25 selected for
both the project and Maven. The CLI baseline is 1.21.6; it resolves the latest
compatible Dev Server without upgrading the application's SDK.

## Running the Application

### Prerequisites

- JDK 25; `fz dev` offers installation through WinGet or Homebrew if no suitable JDK is found
- Git and the Fluxzero CLI
- An IDE such as IntelliJ IDEA

### Dev Server

From the repository directory, in a terminal or Windows PowerShell:

```text
fz dev --background
fz dev status --json
```

Run this in your own terminal. Accept the Java 25 installation prompt if shown;
without WinGet or Homebrew, follow the manual JDK instructions in the preparation guide.
The CLI downloads Dev Server and uses the repository's Maven wrapper; no separate Maven
installation or wrapper check is needed before the first start. Select JDK 25 in your IDE too.

Open the Devboard URL printed by the startup command.
Use Workspace, Tests and Audit trail to inspect the application, test results and messages.
The Dev Server manages the local Test Server, Proxy and auditlog storage; a separate OpenSearch setup is not required.

Complete one start before the course so the tools and dependencies are downloaded.
You are ready when all four applications run, the Meepleverse application tests
pass, Audit trail shows startup messages and `/catalog` returns 35 games.
Run `fz dev stop` afterwards; start again using the same command during the course.

The PSP, supplier and shipper are supplied as executable JARs in `local/`. Dev Server starts each service in its own process on the shared runtime. Work on the Meepleverse application in `src/` throughout the exercises.

### API Endpoints

Open the reported gateway URL to visit the **Meepleverse shop**, or use
`/_fluxzero/dev/#application` to view it inside Devboard. The shop is served at `/shop/`.
It uses the same running backend and exercise services, without Node, npm or a separate frontend server.

Browse the catalog and **Sign in** with a fictional local username. The development IDP accepts
a name without a password or an email code: this is for a participant's own laptop, not a shared
identity service. Use the same name to revisit your profile within this running environment.
**My profile** holds a fictional delivery address (editable) and a personal **Meeple Pay** demo
payment method. There is no balance to top up and no money is taken. The shop remembers you in the same browser for 30 days, including overnight and when you
restart the Dev Server or switch exercise branches in the same checkout. Use **My profile → Sign out**
to switch users.

Choose **Buy now** on a game, or combine titles in the basket. Checkout shows your order
number as soon as the shop receives the order. Choose **View order** to follow its progress.

For the checkout exercise, open two shop tabs on the same local environment before buying.
Place an order in the first tab, then immediately place another in the second tab. Compare
how long it takes to receive each order number. If both arrive quickly, try another pair.
Look up the order numbers in the Devboard audit trail. Where did the second order spend its time?

The original [IntelliJ requests](src/test/java/io/fluxzero/training/meepleverse/orders/orders.http)
remain available independently of the shop. Classic `POST /orders` returns an order number and
`GET /orders/{orderId}` returns its status. That endpoint cannot retrieve member orders;
the shop checks the signed-in customer on both history and receipt requests.

### Catalogue exercise

[Exercise 6: Index the game categories](exercises/06-catalog-facets.md) uses the working shop to compare two backend implementations. The shop reads games and category counts from `GET /catalog/browse` with optional `term` and `category` parameters.

### What order progress means

The shop shows the actual payment result, supplier arrivals, shipment requests and
abort reasons. Delays and failures remain part of the exercises. A shipment request
does **not** prove that the carrier accepted or delivered it. A failed shipment can
have no item details if the command rolled back its shipment event.

The order timeline stores each observation with its event timestamp in
`shop-order-observations`. Reprocessing an event updates the same entry.

The browser polls while the order page is visible and backs off after connection
errors. A failed order POST is never retried automatically: it may already have
created an order. Check My orders and the Audit trail before
explicitly starting another order. This baseline has no POST idempotency key.

Static HTML, CSS, JavaScript and fonts are in `src/main/resources/shop`.
The storefront loads game photos from the URLs in
[`game-images.js`](src/main/resources/shop/game-images.js) as cards come into view.
The browser caches each image according to the source's response headers. If a photo
is unavailable, the card shows the game title and checkout stays available.
See the [image credits](src/main/resources/shop/image-credits.html) for sources and attribution.
The meeple image comes from the supplied Advanced Keynote deck. Poppins is bundled
with its OFL license.

This is a **local course demo** using the existing permissive `SenderProvider`.
Use fictional checkout details. Public hosting or real customer accounts require
a separate authentication and ownership design.

## Testing

**Using IntelliJ IDEA:**

- Run `All tests`

**Using Maven:**

```bash
./mvnw test
```

On Windows, without an active Dev Server:

```powershell
.\mvnw.cmd test
```

When the Dev Server is active, follow its managed test results instead of starting a second build.
