# Agent Instructions — argo-rollouts-quarkus-demo

Quarkus demo application with built-in load generator and bug scenario images. Used to test the AI-powered progressive delivery system end-to-end. This is an experimental project — prioritize simplicity over backwards compatibility.

## Overview

This app serves as the canary deployment target for testing the `rollouts-plugin-metric-ai` and `kubernetes-agent` integration. It includes multiple container image tags representing different scenarios:

- **Stable** — healthy baseline
- **Null pointer** — throws NullPointerException under load
- **Memory leak** — gradual memory consumption leading to OOM
- **Latency drift** — progressive response latency increase, no errors in logs

## Stack

Java 25, Quarkus 3.x with REST endpoints and health checks.

## Development

```bash
./mvnw quarkus:dev
```

Dev UI: `http://localhost:8080/q/dev/`

## Building and Deploying

Use the Quarkus build and image push workflow:

```bash
quarkus build --no-tests && quarkus image push --also-build
```

Then force a pod restart to pull the updated image:

```bash
oc argo rollouts restart quarkus-demo -n quarkus-demo
```

## Load Generation

The app includes an internal load generator that can be triggered via REST endpoint or configuration. See the source code for details.

## Testing

Always write tests for new features. Use `@QuarkusTest` for integration tests.

## Resources

- [Quarkus](https://quarkus.io/guides/)
- [Argo Rollouts](https://argo-rollouts.readthedocs.io/)
