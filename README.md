# Fluxzero training — setup check

Before the training, this repository contains only a small service for checking
that your local development environment works. The course application and
exercises will be made available **at the start of the training**.

## Prepare your laptop

Follow the [preparation guide](https://training.fluxzero.io/advanced/preparation/)
for Windows, macOS or Linux. You need Git, JDK 25, a Java IDE, the Fluxzero CLI
(course baseline 1.21.6), a browser and a terminal. The repository includes Maven
wrappers; no separate Maven installation is needed.

```text
git clone https://github.com/fluxzero-io/meepleverse.git
cd meepleverse
fz dev --background --dev-server-version 1.20.10
fz dev status
```

Use the local gateway URL reported by the CLI; its port is allocated automatically.

1. Open that URL in your browser. It should say **Your Fluxzero development environment is working.**
2. Open the same URL followed by `/_fluxzero/dev/` for Devboard.
3. In **Workspace**, check that `setup-check` is running. In **Tests**, wait for `WelcomeEndpointTest` to pass.
4. In **Audit trail**, select **Web** instead of **Core** and find **GET /api/status**, the request made by the welcome page.
5. Import `pom.xml` in your IDE and select JDK 25 for both the project and Maven.

Dev Server manages compilation, application startup, the local Runtime and the
test run. While it is active, use its test results instead of starting a second
Maven build. Stop it after checking:

```text
fz dev stop
```

Start it once more with the same command to check that restarting also works.
No account or login is needed for this local setup check. Nothing needs changing
in the source code; keep the checkout on `main` until the course starts.

## At the start of the training

Wait until your trainers have published the course application. Then stop Dev
Server, update this same checkout and create your exercise branch:

```text
fz dev stop
git pull --ff-only
git switch -c my/exercise-01
fz dev --background --dev-server-version 1.20.10
```

Reload the Maven project in your IDE if prompted. Use the new gateway URL printed
by the CLI. No fresh clone, merge or reset is needed when updating from this setup
check with an unchanged working tree.
