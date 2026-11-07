# Agent Instructions — argo-rollouts-aiops-dashboard

Quarkus demo dashboard and canary target for the AI-powered progressive delivery system. It serves the rollout UI, exposes health and Prometheus endpoints, and ships four scenario images used to test the agent end to end. This is an experimental project — prioritize simplicity over backwards compatibility.

## Overview

The app is the canary deployment target for `rollouts-plugin-metric-ai` and `kubernetes-agent`. The GitHub Actions workflow builds four images from the same source:

- `v1.stable` — healthy baseline
- `v2.nullpointer` — `NullPointerException` on 20% of user lookups
- `v3.memoryleak` — 1MB allocated per request, latency climbs with heap
- `v4.latency` — latency drift after a 20-second grace period, clean logs

The flags are compiled into each image. They are not runtime configuration.

## Stack

Java 25, Quarkus 3.x with REST endpoints, a Qute dashboard, and a built-in load generator.

## Development

```bash
./mvnw quarkus:dev
```

Dev UI: `http://localhost:8080/q/dev/`

## Building and Deploying

Scenario images are built and pushed by [`.github/workflows/build-scenario-images.yml`](.github/workflows/build-scenario-images.yml) on every push to `main`. Do not build them by hand unless you are debugging the workflow.

The running image is a line in `progressive-delivery/workloads/quarkus-rollouts-demo/base/rollout.yaml`. Change it, commit, and push; Argo CD syncs it. The rolling update is the demo, so there is nothing to restart manually.

For a quick local iteration on the dashboard, push a `:main` image and point the rollout at it:

```bash
quarkus build --no-tests && quarkus image push --also-build
```

## Load generation

Each pod generates 300 requests per second against itself: 80% to `/api/status`, 20% to `/api/user`. Configuration lives under `load.generator.*` in `application.properties`. This is what gives the agent enough samples during the first canary pause.

## Dashboard

The dashboard calls the agent for rollout control (`POST /api/rollout/promote` and `/abort`) through `KUBERNETES_AGENT_URL`. It renders the decision card, the decision-model grader verdict, pause-gate buttons, and the activity feed. Keep the UI aligned with the agent response contract rather than duplicating logic here.

## Testing

Always write tests for new features. Use `@QuarkusTest` for integration tests.

## Resources

- [Quarkus](https://quarkus.io/guides/)
- [Argo Rollouts](https://argo-rollouts.readthedocs.io/)
