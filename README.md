# Argo Rollouts AI Plugin - Demo Application

[![Build and Push](https://github.com/kdubois/argo-rollouts-aiops-dashboard/actions/workflows/build.yml/badge.svg)](https://github.com/kdubois/argo-rollouts-aiops-dashboard/actions/workflows/build.yml)

A demonstration application showcasing AI-powered progressive delivery with Argo Rollouts. This Quarkus-based application integrates with the Argo Rollouts AI Plugin to enable autonomous canary deployment analysis and automated remediation.

## Quick Links

- **[Deployment Guide](DEPLOYMENT.md)** - Complete installation and setup instructions
- **[Demo Script](DEMO_SCRIPT.md)** - Conference presentation guide with step-by-step demo flows
- **[AI Plugin](https://github.com/kdubois/rollouts-plugin-metric-ai)** - Metric provider plugin documentation
- **[Kubernetes Agent](https://github.com/kdubois/kubernetes-aiops-agent)** - Autonomous AI agent documentation

## Architecture

The demo application is part of a larger system that brings AI-powered analysis to Kubernetes deployments:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Kubernetes Cluster                          │
│                                                                  │
│  ┌──────────────────┐                                           │
│  │   Demo App       │  Quarkus application with:                │
│  │                  │  - Health endpoints (/q/health)           │
│  │  Stable + canary │  - Metrics endpoint (/q/metrics)          │
│  │  pods (10 total) │  - Dashboard UI (/)                       │
│  └────────┬─────────┘  - Built-in load generator (300 req/sec)  │
│           │            - Pre-built scenario images:              │
│           │              • v1.stable (99% success)               │
│           │              • v2.nullpointer (20% NPE failures)     │
│           │              • v3.memoryleak (gradual degradation)   │
│           │              • v4.latency (slow-burn latency drift)  │
│           │                                                      │
│           │ Managed by                                           │
│           ▼                                                      │
│  ┌──────────────────┐         ┌────────────────────┐           │
│  │  Argo Rollouts   │────────▶│  OpenShift Route   │           │
│  │                  │         │                    │           │
│  │  - Canary steps  │         │  10% → 60% → 100%  │           │
│  │  - Analysis runs │         │                    │           │
│  └────────┬─────────┘         └────────────────────┘           │
│           │                                                      │
│           │ Triggers analysis                                    │
│           ▼                                                      │
│  ┌──────────────────┐                                           │
│  │   AI Plugin      │  Metric provider that:                    │
│  │                  │  - Sends pod selectors to the agent       │
│  │  (Go binary)     │  - Returns promote or rollback            │
│  └────────┬─────────┘                                           │
│           │                                                      │
│           │ HTTP POST /analyze                                   │
│           ▼                                                      │
│  ┌──────────────────┐                                           │
│  │ Kubernetes Agent │  Autonomous AI agent that:                │
│  │                  │  - Fetches logs and metrics itself        │
│  │  (Quarkus +      │  - Uses an OpenAI-compatible LLM         │
│  │   LangChain4j)   │  - Promotes or rolls back                 │
│  └────────┬─────────┘  - Delegates fixes to remediation agent  │
│           │                                                      │
│           │ A2A (rollback with repoUrl)                          │
│           ▼                                                      │
│  ┌──────────────────┐                                           │
│  │ Remediation      │  Sandboxed coding agent that:             │
│  │ Agent (A2A)      │  - Verifies the fix with tests            │
│  └────────┬─────────┘  - Opens a PR or an issue                  │
│           │                                                      │
└───────────┼──────────────────────────────────────────────────────┘
            │
            │ Creates PRs or issues on failure
            ▼
   ┌────────────────────┐
   │      GitHub        │  Automated remediation:
   │                    │  - Root cause analysis
   │   Pull Requests    │  - Verified code fixes (code bugs)
   │   & Issues         │  - Investigation steps (operational issues)
   └────────────────────┘
```

## Key Features

### Progressive Delivery with AI Analysis

The canary analysis runs on the deployment's own logs and metrics. The agent compares stable with canary and decides whether to promote or roll back.

### Autonomous Decision Making

Unlike traditional metric-based analysis that relies on static thresholds, the AI agent uses large language models to understand context, identify patterns, and make nuanced decisions about deployment health.

### Automated Remediation

When issues are detected, the Kubernetes Agent delegates to the remediation agent, which opens a GitHub pull request with the fix and its test evidence for a patchable code bug, or an issue with investigation steps for an operational problem.

### Pre-Built Scenario Images

The application provides four pre-built container images with different behaviors for demonstration purposes:

- **v1.stable** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable`): Healthy application with 99% success rate
- **v2.nullpointer** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer`): NullPointerException bug on 20% of user lookups
- **v3.memoryleak** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v3.memoryleak`): Memory leak causing gradual performance degradation
- **v4.latency** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v4.latency`): Progressive latency drift with no errors in logs - a metrics-only regression

**Note**: Bug scenarios are baked into the images at build time via GitHub Actions. Switch scenarios by changing the image tag in your rollout. See [SCENARIOS.md](SCENARIOS.md) for detailed information.

## Technology Stack

- **Quarkus**: Supersonic Subatomic Java Framework
- **Quarkus Web Bundler**: Zero-config bundling for web assets
- **Qute Templates**: Type-safe templating for the dashboard
- **SmallRye Health**: Health check endpoints
- **Micrometer**: Metrics collection and exposure
- **Argo Rollouts**: Progressive delivery controller

## CI/CD

The project uses GitHub Actions for automated builds and deployments:

- **Automated Scenario Builds**: Every push to `main` triggers builds of all four scenario images (see `.github/workflows/build-scenario-images.yml`)
- **Pull Request Validation**: PRs are built and tested automatically
- **Container Images**: Available at `ghcr.io/kdubois/argo-rollouts-quarkus-demo`
- **Dependency Management**: Dependabot automatically creates PRs for dependency updates

Container images are tagged with:
- `v1.stable`, `v2.nullpointer`, `v3.memoryleak`, `v4.latency` - Scenario-specific images
- `stable-latest`, `null-pointer-bug-latest`, `memory-leak-latest`, `latency-drift-latest` - Latest builds of each scenario

Merging to `main` also tags the stable build with an immutable `stable-<commit sha>` tag and opens a PR in
[progressive-delivery](https://github.com/kdubois/progressive-delivery) pointing the demo rollout at that build. Merge the
PR and Argo CD rolls it out through the canary analysis. Scenario switching (`v2.nullpointer`, `v3.memoryleak`, …) stays a
one-line manifest edit. The manifest PR needs the `PROGRESSIVE_DELIVERY_TOKEN` repo secret: a token with contents
read/write and pull-request write access to `kdubois/progressive-delivery`.

## Quick Start

### Prerequisites

- Java 25 or later
- Maven 3.8 or later
- Docker (for containerization)
- Kubernetes cluster (for deployment)

### Running Locally

Start the application in development mode with live coding enabled:

```bash
./mvnw quarkus:dev
```

The application will be available at:
- **Application**: http://localhost:8080
- **Dashboard**: http://localhost:8080/
- **Health**: http://localhost:8080/q/health
- **Metrics**: http://localhost:8080/q/metrics
- **Dev UI**: http://localhost:8080/q/dev

### Building for Production

Build the application as a JAR:

```bash
./mvnw package
```

The runnable JAR will be created at `target/quarkus-app/quarkus-run.jar`.

Run the application:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Building Docker Images

Build a JVM-based Docker image:

```bash
docker build -f src/main/docker/Dockerfile.jvm -t ghcr.io/kdubois/argo-rollouts-quarkus-demo:latest .
```

Build a native executable Docker image (requires GraalVM):

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native -t ghcr.io/kdubois/argo-rollouts-quarkus-demo:native .
```

**Note**: For scenario-specific images, see the GitHub Actions workflow at `.github/workflows/build-scenario-images.yml` which automatically builds all four scenarios.

### Using the Makefile

The project includes a Makefile with common tasks:

```bash
# Show all available targets
make help

# Build the application
make build

# Build and push Docker image
make docker-build docker-push

# Run in dev mode
make dev

# Run tests
make test
```

## Deployment to Kubernetes

The demo app is deployed through GitOps: the rollout, AnalysisTemplate, and services live in the [progressive-delivery](../progressive-delivery) repository, and Argo CD syncs them into the cluster. To change the deployment, edit that repository and push — there are no manifests to apply from this repo. To watch the rollout:

```bash
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

For the full setup including Argo Rollouts and the AI plugin, see the [Deployment Guide](DEPLOYMENT.md) and the [progressive-delivery README](../progressive-delivery/README.md).

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ROLLOUT_NAME` | quarkus-demo | Name of the Argo Rollout |
| `ROLLOUT_NAMESPACE` | quarkus-demo | Namespace of the Argo Rollout |
| `KUBERNETES_AGENT_URL` | http://kubernetes-agent... | URL of the Kubernetes Agent for A2A communication |
| `QUARKUS_HTTP_PORT` | 8080 | HTTP port for the application |

### Switching Between Scenarios

Bug scenarios are **baked into the container images** at build time, not configured via environment variables. The active image is a line in the `Rollout` manifest in the [progressive-delivery](../progressive-delivery) repository, so scenario changes go through Git:

```bash
cd ../progressive-delivery
# Uncomment the image line for the scenario you want, comment the current one
vim workloads/quarkus-rollouts-demo/base/rollout.yaml
git add . && git commit -m "demo: run the memory leak scenario" && git push
```

Argo CD syncs the new image and Argo Rollouts starts a canary. The available tags are:

| Image | Behaviour |
|---|---|
| `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable` | Healthy baseline, 99% success |
| `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer` | NullPointerException on 20% of user lookups |
| `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v3.memoryleak` | Allocates 1MB per request, latency climbs to 300ms |
| `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v4.latency` | Latency drifts after 20s, no errors in logs |

See [SCENARIOS.md](SCENARIOS.md) for detailed information about each scenario.

## Demo Scenarios

Each rollout gets two analyses: one during the initial 10% pause, and one at 60% weight. Both must pass before full promotion.

### Scenario 1: Successful Deployment

Deploy a version and watch the AI analyze and promote it:

```bash
# In progressive-delivery: set the rollout image to v1.stable, commit, push
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

The AI analyzes the canary at 10% and 60% traffic, then promotes to 100% if healthy. Both measurements report a passed quality check in the dashboard.

### Scenario 2: Code Bug with Rollback and PR

Deploy the NullPointerException image and watch the AI detect and roll back:

```bash
# In progressive-delivery: set the rollout image to v2.nullpointer, commit, push
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

The logs show `NullPointerException` in the canary only. The analysis rolls back, then delegates to the remediation agent, which reproduces the bug, patches `UserResource`, runs the tests, and opens a pull request with the verification evidence.

### Scenario 3: Memory Leak with Rollback and Issue

Deploy the memory leak image. The canary allocates 1MB per request with no release, so heap and latency climb while the logs only carry vague warnings.

```bash
# In progressive-delivery: set the rollout image to v3.memoryleak, commit, push
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

This is an operational problem, not a patchable code defect, so the remediation agent opens an issue with the evidence and investigation steps instead of a pull request.

### Scenario 4: Metrics-Only Regression

Deploy a version that looks healthy at first and degrades slowly, showing why recurring analysis matters:

```bash
# In progressive-delivery: set the rollout image to v4.latency, commit, push
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

The canary adds no delay for the first 20 seconds of pod uptime, then adds 5ms per second of uptime to every request, capped at 250ms. An analysis early in the rollout sees baseline latency and passes. After 30-60 seconds of load the canary p95 latency is 2-5x the stable baseline and the analysis fails. There are no errors and no exceptions - the success rate stays at 99% and the logs stay clean, so only the latency metrics reveal the regression.

## Monitoring and Observability

### Health Checks

The application exposes health endpoints:

```bash
# Liveness probe
curl http://localhost:8080/q/health/live

# Readiness probe
curl http://localhost:8080/q/health/ready

# Full health check
curl http://localhost:8080/q/health
```

### Metrics

Prometheus-compatible metrics are available:

```bash
curl http://localhost:8080/q/metrics
```

Key metrics include:
- HTTP request counts and durations
- JVM memory and CPU usage
- Application-specific business metrics

### Dashboard

The application includes a web dashboard showing:
- Current deployment status
- Real-time metrics
- Rollout progress
- Analysis results
- The decision model's grader assessment (verdict, probability gauge, self-reported confidence)
- The agent activity feed, including grader verdicts, pause-gate holds, and model escalations

When the pause gate holds a rollout (the grader disputed the analysis), the
dashboard shows a pulsing traffic bar and Promote/Abort buttons in the decision
card. Both call the kubernetes-agent (`POST /api/rollout/promote` and
`POST /api/rollout/abort`), which drives the Argo Rollouts API.

Access the dashboard at: http://localhost:8080/

## Troubleshooting

### Application Won't Start

Check the logs for errors:

```bash
# Local development
./mvnw quarkus:dev

# Kubernetes
kubectl logs -n quarkus-demo -l app=quarkus-demo
```

Common issues:
- Port 8080 already in use
- Missing dependencies
- Configuration errors

### Health Checks Failing

Verify the health endpoints:

```bash
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

Check for:
- Application startup errors
- Resource constraints
- External dependency failures

### Metrics Not Available

Ensure the metrics endpoint is accessible:

```bash
curl http://localhost:8080/q/metrics
```

Verify:
- Micrometer is properly configured
- Prometheus annotations are present
- No network policies blocking access

## Contributing

This is a demonstration application for the Argo Rollouts AI Plugin. Contributions are welcome to improve the demo experience.

When contributing:
1. Follow the existing code style
2. Add tests for new features
3. Update documentation
4. Keep commits focused and well-described

## Related Projects

- **[Argo Rollouts](https://github.com/argoproj/argo-rollouts)**: Progressive delivery for Kubernetes
- **[rollouts-plugin-metric-ai](https://github.com/kdubois/rollouts-plugin-metric-ai)**: AI-powered metric provider plugin
- **[kubernetes-agent](https://github.com/kdubois/kubernetes-aiops-agent)**: Autonomous Kubernetes debugging agent
- **[Quarkus](https://quarkus.io)**: Supersonic Subatomic Java Framework

## License

This project is licensed under the Apache License 2.0.

## Support

For questions or issues:
- Check the [Deployment Guide](DEPLOYMENT.md) for setup help
- Review the [Demo Script](DEMO_SCRIPT.md) for usage examples
- Open an issue in the GitHub repository
- Consult the related project documentation
