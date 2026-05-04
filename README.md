# Argo Rollouts AI Plugin - Demo Application

[![Build and Push](https://github.com/kdubois/argo-rollouts-quarkus-demo/actions/workflows/build.yml/badge.svg)](https://github.com/kdubois/argo-rollouts-quarkus-demo/actions/workflows/build.yml)

A demonstration application showcasing AI-powered progressive delivery with Argo Rollouts. This Quarkus-based application integrates with the Argo Rollouts AI Plugin to enable autonomous canary deployment analysis and automated remediation.

## Quick Links

- **[Deployment Guide](DEPLOYMENT.md)** - Complete installation and setup instructions
- **[Demo Script](DEMO_SCRIPT.md)** - Conference presentation guide with step-by-step demo flows
- **[AI Plugin](https://github.com/kdubois/rollouts-plugin-metric-ai)** - Metric provider plugin documentation
- **[Kubernetes Agent](https://github.com/carlossg/kubernetes-agent)** - Autonomous AI agent documentation

## Architecture

The demo application is part of a larger system that brings AI-powered analysis to Kubernetes deployments:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Kubernetes Cluster                          │
│                                                                  │
│  ┌──────────────────┐                                           │
│  │   Demo App       │  Quarkus application with:                │
│  │                  │  - Health endpoints (/q/health)           │
│  │  Stable: 3 pods  │  - Metrics endpoint (/q/metrics)          │
│  │  Canary: 1 pod   │  - Dashboard UI (/)                       │
│  └────────┬─────────┘  - Built-in load generator (50 req/sec)   │
│           │            - Pre-built scenario images:              │
│           │              • v1.stable (99% success)               │
│           │              • v2.nullpointer (20% NPE failures)     │
│           │              • v3.memoryleak (gradual degradation)   │
│           │                                                      │
│           │ Managed by                                           │
│           ▼                                                      │
│  ┌──────────────────┐         ┌────────────────────┐           │
│  │  Argo Rollouts   │────────▶│  Gateway API       │           │
│  │                  │         │  (HTTPRoute)       │           │
│  │  - Canary steps  │         │                    │           │
│  │  - Analysis runs │         │  10% → 60% → 100%  │           │
│  └────────┬─────────┘         └────────────────────┘           │
│           │                                                      │
│           │ Triggers analysis                                    │
│           ▼                                                      │
│  ┌──────────────────┐                                           │
│  │   AI Plugin      │  Metric provider that:                    │
│  │                  │  - Collects pod logs                      │
│  │  (Go binary)     │  - Delegates to Kubernetes Agent          │
│  └────────┬─────────┘  - Returns PROCEED/ROLLBACK decision      │
│           │                                                      │
│           │ A2A Protocol (HTTP/JSON)                             │
│           ▼                                                      │
│  ┌──────────────────┐                                           │
│  │ Kubernetes Agent │  Autonomous AI agent that:                │
│  │                  │  - Analyzes logs and metrics              │
│  │  (Quarkus +      │  - Uses LLM (Gemini/OpenAI)               │
│  │   LangChain4j)   │  - Makes deployment decisions             │
│  └────────┬─────────┘  - Creates GitHub PRs with fixes          │
│           │                                                      │
└───────────┼──────────────────────────────────────────────────────┘
            │
            │ Creates PRs/Issues on failure
            ▼
   ┌────────────────────┐
   │      GitHub        │  Automated remediation:
   │                    │  - Root cause analysis
   │   Pull Requests    │  - Proposed code fixes (fixable bugs)
   │   & Issues         │  - Investigation steps (complex issues)
   └────────────────────┘
```

## Key Features

### Progressive Delivery with AI Analysis

The application demonstrates how AI can enhance progressive delivery by analyzing canary deployments in real-time. The system automatically decides whether to proceed with or rollback deployments based on intelligent analysis of logs, metrics, and events.

### Autonomous Decision Making

Unlike traditional metric-based analysis that relies on static thresholds, the AI agent uses large language models to understand context, identify patterns, and make nuanced decisions about deployment health.

### Automated Remediation

When issues are detected, the Kubernetes Agent automatically creates GitHub pull requests with detailed root cause analysis and proposed fixes, enabling rapid response to deployment problems.

### Pre-Built Scenario Images

The application provides three pre-built container images with different behaviors for demonstration purposes:

- **v1.stable** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable`): Healthy application with 99% success rate
- **v2.nullpointer** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer`): NullPointerException bug affecting 20% of requests
- **v3.memoryleak** (`ghcr.io/kdubois/argo-rollouts-quarkus-demo:v3.memoryleak`): Memory leak causing gradual performance degradation

**Note**: Bug scenarios are baked into the images at build time via GitHub Actions. Switch scenarios by changing the image tag in your rollout. See [SCENARIOS.md](SCENARIOS.md) for detailed information.

## Technology Stack

- **Quarkus 3.32.1**: Supersonic Subatomic Java Framework
- **Quarkus Web Bundler**: Zero-config bundling for web assets
- **Qute Templates**: Type-safe templating for the dashboard
- **SmallRye Health**: Health check endpoints
- **Micrometer**: Metrics collection and exposure
- **Argo Rollouts**: Progressive delivery controller

## CI/CD

The project uses GitHub Actions for automated builds and deployments:

- **Automated Scenario Builds**: Every push to `main` triggers builds of all three scenario images (see `.github/workflows/build-scenario-images.yml`)
- **Pull Request Validation**: PRs are built and tested automatically
- **Container Images**: Available at `ghcr.io/kdubois/argo-rollouts-quarkus-demo`
- **Dependency Management**: Dependabot automatically creates PRs for dependency updates

Container images are tagged with:
- `v1.stable`, `v2.nullpointer`, `v3.memoryleak` - Scenario-specific images
- `stable-latest`, `null-pointer-bug-latest`, `memory-leak-latest` - Latest builds of each scenario

## Quick Start

### Prerequisites

- Java 21 or later
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

**Note**: For scenario-specific images, see the GitHub Actions workflow at `.github/workflows/build-scenario-images.yml` which automatically builds all three scenarios.

### Using the Makefile

The project includes a Makefile with common tasks:

```bash
# Show all available targets
make help

# Build the application
make build

# Build and push Docker image
make docker-build docker-push

# Deploy to Kubernetes
make deploy

# Run in dev mode
make dev

# Run tests
make test
```

## Deployment to Kubernetes

For complete deployment instructions including Argo Rollouts, Istio, and the AI Plugin, see the [Deployment Guide](DEPLOYMENT.md).

Quick deployment using kustomize:

```bash
# Deploy all resources
kubectl apply -k kubernetes/

# Watch rollout progress
kubectl argo rollouts get rollout demo-app -n demo-app --watch
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ROLLOUT_NAME` | quarkus-demo | Name of the Argo Rollout |
| `ROLLOUT_NAMESPACE` | quarkus-demo | Namespace of the Argo Rollout |
| `KUBERNETES_AGENT_URL` | http://kubernetes-agent... | URL of the Kubernetes Agent for A2A communication |
| `QUARKUS_HTTP_PORT` | 8080 | HTTP port for the application |

### Switching Between Scenarios

Bug scenarios are **baked into the container images** at build time, not configured via environment variables. To switch scenarios, change the image tag:

```bash
# Deploy stable version (99% success rate)
kubectl set image rollout/quarkus-demo quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable -n quarkus-demo

# Deploy NullPointerException bug (20% failure rate)
kubectl set image rollout/quarkus-demo quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer -n quarkus-demo

# Deploy memory leak (gradual degradation)
kubectl set image rollout/quarkus-demo quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v3.memoryleak -n quarkus-demo
```

See [SCENARIOS.md](SCENARIOS.md) for detailed information about each scenario.

## Demo Scenarios

### Scenario 1: Successful Deployment

Deploy a new version and watch the AI analyze and promote it:

```bash
# Trigger a new rollout with stable image
kubectl set image rollout/quarkus-demo quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable -n quarkus-demo

# Watch the analysis
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

The AI will analyze the canary at 20% and 50% traffic, then automatically promote to 100% if healthy.

### Scenario 2: Failed Deployment with Rollback

Deploy a buggy version and watch the AI detect and rollback:

```bash
# Deploy buggy version with NullPointerException
kubectl set image rollout/quarkus-demo quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer -n quarkus-demo

# Watch the rollback
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

The AI will detect elevated error rates and automatically rollback the deployment. A GitHub PR will be created with the fix.

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

Access the dashboard at: http://localhost:8080/

## Development

### Project Structure

```
demo-app/
├── src/
│   ├── main/
│   │   ├── docker/              # Dockerfiles for different builds
│   │   ├── java/                # Java source code
│   │   │   └── dev/kevindubois/
│   │   │       ├── demo/        # Demo-specific code
│   │   │       │   ├── DashboardResource.java
│   │   │       │   ├── MetricsResource.java
│   │   │       │   └── model/   # Data models
│   │   │       ├── GreetingResource.java
│   │   │       ├── MyLivenessCheck.java
│   │   │       └── MyReadinessCheck.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── templates/       # Qute templates
│   │       └── web/             # Web assets (JS, CSS)
│   └── test/                    # Test code
├── kubernetes/                  # Kubernetes manifests
│   ├── namespace.yaml
│   ├── rollout.yaml            # Argo Rollouts configuration
│   ├── analysistemplate.yaml   # AI analysis configuration
│   ├── service-*.yaml          # Kubernetes services
│   ├── gateway.yaml            # Istio gateway
│   ├── virtualservice.yaml     # Istio virtual service
│   └── kustomization.yaml      # Kustomize configuration
├── Makefile                    # Build automation
├── pom.xml                     # Maven configuration
├── README.md                   # This file
├── DEPLOYMENT.md               # Deployment guide
└── DEMO_SCRIPT.md              # Conference demo script
```

### Adding New Features

When adding new features to the demo application:

1. Follow the existing code structure and patterns
2. Add appropriate health checks if needed
3. Expose relevant metrics for monitoring
4. Update the dashboard if the feature affects user experience
5. Add tests for new functionality
6. Update documentation

### Code Formatting

Format code using the Maven formatter:

```bash
./mvnw fmt:format
```

Or use the Makefile:

```bash
make format
```

## Testing

Run the test suite:

```bash
./mvnw test
```

Run integration tests:

```bash
./mvnw verify
```

Run tests with coverage:

```bash
./mvnw verify jacoco:report
```

## Troubleshooting

### Application Won't Start

Check the logs for errors:

```bash
# Local development
./mvnw quarkus:dev

# Kubernetes
kubectl logs -n demo-app -l app=demo-app
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
- **[kubernetes-agent](https://github.com/carlossg/kubernetes-agent)**: Autonomous Kubernetes debugging agent
- **[Quarkus](https://quarkus.io)**: Supersonic Subatomic Java Framework

## License

This project is licensed under the Apache License 2.0.

## Support

For questions or issues:
- Check the [Deployment Guide](DEPLOYMENT.md) for setup help
- Review the [Demo Script](DEMO_SCRIPT.md) for usage examples
- Open an issue in the GitHub repository
- Consult the related project documentation

---

**Ready to see AI-powered progressive delivery in action?** Follow the [Deployment Guide](DEPLOYMENT.md) to get started!
