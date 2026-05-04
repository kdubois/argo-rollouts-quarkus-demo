# Kubernetes Manifests for Argo Rollouts Quarkus Demo

This directory contains Kubernetes manifests for deploying the Quarkus demo application with Argo Rollouts progressive delivery and AI-powered analysis.

## Architecture

The deployment uses:
- **Argo Rollouts**: Progressive delivery controller for canary deployments
- **Kubernetes Gateway API**: Modern traffic management (replaces Istio VirtualService/Gateway)
- **AI Metrics Plugin**: Autonomous analysis using the Kubernetes Agent
- **Canary Strategy**: Gradual rollout with automated analysis at each step

## Prerequisites

1. **Kubernetes cluster** (v1.24+) with Gateway API support
2. **Argo Rollouts** installed with the AI metrics plugin
3. **Gateway API CRDs** installed
4. **Kubernetes Agent** deployed (for AI analysis)
5. **kubectl** and **kustomize** CLI tools

### Install Gateway API CRDs

```bash
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.0.0/standard-install.yaml
```

### Install Argo Rollouts

```bash
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
```

## Quick Start

Deploy the complete stack using kustomize:

```bash
# Deploy all resources
kubectl apply -k .

# Watch rollout progress
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# Access the application
kubectl port-forward -n quarkus-demo svc/quarkus-demo-stable 8080:8080
```

Open http://localhost:8080 in your browser.

## Manifest Files

### Core Resources

- **`namespace.yaml`**: Creates the `quarkus-demo` namespace
- **`rbac.yaml`**: ServiceAccount, Role, and RoleBinding for the application
- **`service-stable.yaml`**: Service for stable pods
- **`service-canary.yaml`**: Service for canary pods

### Traffic Management

- **`gateway.yaml`**: Kubernetes Gateway API resources
  - Gateway: Entry point for HTTP traffic
  - HTTPRoute: Routes traffic between stable and canary services

### Progressive Delivery

- **`rollout.yaml`**: Argo Rollouts resource defining:
  - Canary deployment strategy
  - Traffic splitting steps (20% → 50% → 80% → 100%)
  - Analysis configuration
  - Pod specifications

- **`analysistemplate.yaml`**: AI-powered analysis configuration
  - Connects to Kubernetes Agent for log analysis
  - Configures pod label selectors
  - Sets GitHub integration for issue creation

### Build Configuration

- **`kustomization.yaml`**: Kustomize configuration for easy customization

## Deployment Scenarios

### Scenario 1: Successful Deployment

Deploy with default settings (success mode):

```bash
kubectl apply -k .
```

The rollout will:
1. Deploy canary pods (20% traffic)
2. Run AI analysis on logs
3. Gradually increase traffic if healthy
4. Complete rollout to 100%

### Scenario 2: Failed Deployment with Auto-Rollback

Deploy with failure mode to test automatic rollback:

```bash
# Edit rollout.yaml to set SCENARIO_MODE=failure
kubectl patch rollout quarkus-demo -n quarkus-demo --type merge -p '
{
  "spec": {
    "template": {
      "spec": {
        "containers": [{
          "name": "quarkus-demo",
          "env": [{
            "name": "SCENARIO_MODE",
            "value": "failure"
          }]
        }]
      }
    }
  }
}'

# Trigger a new rollout
kubectl argo rollouts set image quarkus-demo -n quarkus-demo \
  quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.0.0
```

The AI will detect errors and automatically rollback the deployment.

### Scenario 3: Update Image Version

Update to a new version:

```bash
# Using kustomize
cd kubernetes/
kustomize edit set image ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.0.0
kubectl apply -k .

# Or using kubectl directly
kubectl argo rollouts set image quarkus-demo -n quarkus-demo \
  quarkus-demo=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.0.0
```

## Configuration

### Environment Variables

Configure the application behavior via environment variables in `rollout.yaml`:

| Variable | Default | Description |
|----------|---------|-------------|
| `SCENARIO_MODE` | success | Deployment scenario (success, failure) |
| `APP_VERSION` | 1.0.0 | Application version displayed |
| `ROLLOUT_NAME` | quarkus-demo | Name of the rollout resource |
| `ROLLOUT_NAMESPACE` | quarkus-demo | Namespace of the rollout |
| `KUBERNETES_AGENT_URL` | http://kubernetes-agent.argo-rollouts.svc.cluster.local:8080 | URL of the AI agent |

### Canary Steps

Modify the canary steps in `rollout.yaml`:

```yaml
steps:
- setWeight: 20      # 20% traffic to canary
- pause: { duration: 10s }
- setWeight: 50      # 50% traffic to canary
- pause: { duration: 30s }
- setWeight: 100     # 100% traffic to canary
```

### Analysis Configuration

Customize AI analysis in `analysistemplate.yaml`:

```yaml
provider:
  plugin:
    argoproj-labs/metric-ai:
      agentUrl: http://kubernetes-agent.argo-rollouts.svc.cluster.local:8080
      stableLabel: role=stable
      canaryLabel: role=canary
      githubUrl: https://github.com/your-org/your-repo
      extraPrompt: "Focus on error rates and performance"
```

## Monitoring

### View Rollout Status

```bash
# Get rollout status
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo

# Watch rollout progress
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# View rollout history
kubectl argo rollouts history rollout quarkus-demo -n quarkus-demo
```

### View Analysis Results

```bash
# List analysis runs
kubectl get analysisrun -n quarkus-demo

# View specific analysis
kubectl describe analysisrun <name> -n quarkus-demo

# View analysis logs
kubectl logs -n quarkus-demo -l analysisrun=<name>
```

### Application Logs

```bash
# View stable pod logs
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=stable

# View canary pod logs
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=canary

# Follow logs
kubectl logs -n quarkus-demo -l app=quarkus-demo --follow
```

## Troubleshooting

### Rollout Stuck in Progressing

```bash
# Check rollout status
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo

# Check analysis runs
kubectl get analysisrun -n quarkus-demo

# Check pod status
kubectl get pods -n quarkus-demo
```

### Analysis Failures

```bash
# Check if agent is reachable
kubectl exec -n quarkus-demo deployment/quarkus-demo -- \
  curl http://kubernetes-agent.argo-rollouts.svc.cluster.local:8080/q/health

# Check agent logs
kubectl logs -n argo-rollouts deployment/kubernetes-agent

# Verify pod labels
kubectl get pods -n quarkus-demo --show-labels
```

### Gateway Not Working

```bash
# Check Gateway status
kubectl get gateway -n quarkus-demo
kubectl describe gateway quarkus-demo-gateway -n quarkus-demo

# Check HTTPRoute status
kubectl get httproute -n quarkus-demo
kubectl describe httproute quarkus-demo-route -n quarkus-demo

# Verify services
kubectl get svc -n quarkus-demo
```

## Manual Rollout Control

### Promote Rollout

```bash
kubectl argo rollouts promote quarkus-demo -n quarkus-demo
```

### Abort Rollout

```bash
kubectl argo rollouts abort quarkus-demo -n quarkus-demo
```

### Retry Rollout

```bash
kubectl argo rollouts retry rollout quarkus-demo -n quarkus-demo
```

### Restart Rollout

```bash
kubectl argo rollouts restart quarkus-demo -n quarkus-demo
```

## Cleanup

Remove all resources:

```bash
kubectl delete -k .
```

Or delete the namespace:

```bash
kubectl delete namespace quarkus-demo
```

## Additional Resources

- [Argo Rollouts Documentation](https://argo-rollouts.readthedocs.io/)
- [Kubernetes Gateway API](https://gateway-api.sigs.k8s.io/)
- [AI Metrics Plugin](https://github.com/kdubois/rollouts-plugin-metric-ai)
- [Kubernetes Agent](https://github.com/kdubois/kubernetes-agent)
- [Main Project README](../README.md)
- [Deployment Guide](../DEPLOYMENT.md)