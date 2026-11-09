# Meepleverse

This repository contains backend sources used in the **2-day in-person Flux Advanced Training**.

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
- Review recently released and upcoming Flux features

## Setting up

Before continuing, make sure you have created your own fork of
the [public Meepleverse repository](https://github.com/fluxzero-io/training-meepleverse). Next, clone this fork on your
machine, then open the project in Intellij IDEA.

## Running the Application

### Prerequisites

- Java 24 or higher
- Maven 3.6.3+

### Quick Start

Start the complete application stack:

**From IntelliJ IDEA:**

- Run `docker-compose` (only needed once)

This will start:

- Fluxzero Test Server on port 8888
- Fluxzero Proxy on port 8080
- OpenSearch Dashboards on localhost:5601

And once everything is running:

- Run `Meepleverse`

### API Endpoints

Once running, you can create new board game orders from `orders.http`.

## Testing

**Using IntelliJ IDEA:**

- Run `All tests`

**Using Maven:**

```bash
./mvnw test
```