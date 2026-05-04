# Deployment Guide - Argo Rollouts AI Plugin Demo

This guide provides comprehensive instructions for deploying the Argo Rollouts AI Plugin demo application. Follow these steps to set up a complete environment for demonstrating AI-powered progressive delivery.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Architecture Overview](#architecture-overview)
- [Installation Steps](#installation-steps)
  - [1. Install Argo Rollouts](#1-install-argo-rollouts)
  - [2. Install Istio Service Mesh](#2-install-istio-service-mesh)
  - [3. Deploy Kubernetes Agent](#3-deploy-kubernetes-agent)
  - [4. Install AI Plugin](#4-install-ai-plugin)
  - [5. Deploy Demo Application](#5-deploy-demo-application)
  - [6. Configure GitHub Integration](#6-configure-github-integration)
- [Verification](#verification)
- [Configuration Reference](#configuration-reference)
- [Troubleshooting](#troubleshooting)
- [Uninstallation](#uninstallation)

---

## Prerequisites

Before beginning the installation, ensure you have the following:

### Required Tools

- **Kubernetes cluster** (version 1.24 or later)
  - Minikube, Kind, or any cloud provider (GKE, EKS, AKS)
  - At least 4 CPU cores and 8GB RAM available
- **kubectl** (version 1.24 or later)
  - Configured to access your cluster
- **Argo Rollouts kubectl plugin** (version 1.6 or later)
  - Install with: `brew install argoproj/tap/kubectl-argo-rollouts`
- **kustomize** (version 4.5 or later)
  - Usually bundled with kubectl
- **Docker** (for building images)
  - Version 20.10 or later

### Required Credentials

- **Google API Key** or **OpenAI API Key**
  - For the Kubernetes Agent's AI capabilities
  - Get Gemini key from: https://makersuite.google.com/app/apikey
  - Get OpenAI key from: https://platform.openai.com/api-keys
- **GitHub Personal Access Token**
  - Required for automated PR creation
  - Needs `repo` scope for private repositories
  - Create at: https://github.com/settings/tokens

### Verify Prerequisites

```bash
# Check Kubernetes cluster access
kubectl cluster-info
kubectl get nodes

# Check kubectl version
kubectl version --client

# Check Argo Rollouts plugin
kubectl argo rollouts version

# Check kustomize
kubectl kustomize --help
```

---

## Architecture Overview

The demo application consists of several components working together:

```
┌─────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                       │
│                                                              │
│  ┌────────────────┐         ┌──────────────────┐           │
│  │   Demo App     │────────▶│  Istio Gateway   │           │
│  │  (Namespace:   │         │  (Traffic Split) │           │
│  │   demo-app)    │         └──────────────────┘           │
│  │                │                                          │
│  │ - Stable Pods  │                                          │
│  │ - Canary Pods  │                                          │
│  └────────┬───────┘                                          │
│           │                                                  │
│           │ Managed by                                       │
│           ▼                                                  │
│  ┌────────────────┐         ┌──────────────────┐           │
│  │ Argo Rollouts  │────────▶│   AI Plugin      │           │
│  │  Controller    │         │ (Metric Provider)│           │
│  │                │         └────────┬─────────┘           │
│  └────────────────┘                  │                      │
│                                      │ A2A Protocol         │
│                                      ▼                      │
│                          ┌──────────────────┐               │
│                          │ Kubernetes Agent │               │
│                          │  (AI Analysis)   │               │
│                          └────────┬─────────┘               │
│                                   │                         │
└───────────────────────────────────┼─────────────────────────┘
                                    │
                                    ▼
                          ┌──────────────────┐
                          │     GitHub       │
                          │  (PR Creation)   │
                          └──────────────────┘
```

**Component Responsibilities**:

- **Demo App**: Quarkus application that exposes metrics and health endpoints
- **Argo Rollouts**: Manages progressive delivery with canary deployments
- **AI Plugin**: Collects metrics and delegates analysis to the Kubernetes Agent
- **Kubernetes Agent**: Autonomous AI agent that analyzes deployments and creates PRs
- **Istio**: Service mesh that handles traffic splitting between stable and canary

---

## Installation Steps

### 1. Install Argo Rollouts

Argo Rollouts is the progressive delivery controller that manages canary deployments.

#### Install the Controller

```bash
# Create namespace
kubectl create namespace argo-rollouts

# Install Argo Rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# Wait for rollout to be ready
kubectl wait --for=condition=available --timeout=300s \
  deployment/argo-rollouts -n argo-rollouts
```

**Expected Output**:
```
namespace/argo-rollouts created
customresourcedefinition.apiextensions.k8s.io/analysisruns.argoproj.io created
customresourcedefinition.apiextensions.k8s.io/analysistemplates.argoproj.io created
customresourcedefinition.apiextensions.k8s.io/clusteranalysistemplates.argoproj.io created
customresourcedefinition.apiextensions.k8s.io/experiments.argoproj.io created
customresourcedefinition.apiextensions.k8s.io/rollouts.argoproj.io created
serviceaccount/argo-rollouts created
clusterrole.rbac.authorization.k8s.io/argo-rollouts created
clusterrolebinding.rbac.authorization.k8s.io/argo-rollouts created
service/argo-rollouts-metrics created
deployment.apps/argo-rollouts created
deployment.apps/argo-rollouts condition met
```

#### Verify Installation

```bash
# Check pods
kubectl get pods -n argo-rollouts

# Check CRDs
kubectl get crd | grep argoproj.io
```

**Expected Output**:
```
NAME                             READY   STATUS    RESTARTS   AGE
argo-rollouts-7d8f9c5b6d-abc12  1/1     Running   0          1m

analysisruns.argoproj.io                      2024-01-15T10:00:00Z
analysistemplates.argoproj.io                 2024-01-15T10:00:00Z
clusteranalysistemplates.argoproj.io          2024-01-15T10:00:00Z
experiments.argoproj.io                       2024-01-15T10:00:00Z
rollouts.argoproj.io                          2024-01-15T10:00:00Z
```

---

### 2. Install Istio Service Mesh

Istio provides traffic management capabilities for canary deployments.

#### Install Istio

```bash
# Download Istio
curl -L https://istio.io/downloadIstio | sh -
cd istio-*

# Add istioctl to PATH
export PATH=$PWD/bin:$PATH

# Install Istio with demo profile
istioctl install --set profile=demo -y

# Enable automatic sidecar injection for demo-app namespace
kubectl create namespace demo-app
kubectl label namespace demo-app istio-injection=enabled
```

**Expected Output**:
```
✔ Istio core installed
✔ Istiod installed
✔ Ingress gateways installed
✔ Egress gateways installed
✔ Installation complete
namespace/demo-app created
namespace/demo-app labeled
```

#### Verify Istio Installation

```bash
# Check Istio pods
kubectl get pods -n istio-system

# Verify namespace label
kubectl get namespace demo-app --show-labels
```

**Expected Output**:
```
NAME                                    READY   STATUS    RESTARTS   AGE
istio-ingressgateway-7d8f9c5b6d-abc12  1/1     Running   0          2m
istiod-789abc123def-xyz45               1/1     Running   0          2m

NAME       STATUS   AGE   LABELS
demo-app   Active   1m    istio-injection=enabled
```

---

### 3. Deploy Kubernetes Agent

The Kubernetes Agent is an autonomous AI agent that analyzes deployments and creates GitHub PRs.

#### Create Secret for API Keys

```bash
# Create secret with your API keys
kubectl create secret generic kubernetes-agent -n argo-rollouts \
  --from-literal=GOOGLE_API_KEY='your-google-api-key' \
  --from-literal=GITHUB_TOKEN='your-github-token' \
  --from-literal=GIT_USERNAME='kubernetes-agent' \
  --from-literal=GIT_EMAIL='agent@example.com'

# Or for OpenAI:
kubectl create secret generic kubernetes-agent -n argo-rollouts \
  --from-literal=OPENAI_API_KEY='your-openai-api-key' \
  --from-literal=GITHUB_TOKEN='your-github-token' \
  --from-literal=GIT_USERNAME='kubernetes-agent' \
  --from-literal=GIT_EMAIL='agent@example.com'
```

#### Deploy the Agent

```bash
# Navigate to kubernetes-agent directory
cd kubernetes-agent

# Deploy using kustomize
kubectl apply -k deployment/

# Wait for agent to be ready
kubectl wait --for=condition=available --timeout=300s \
  deployment/kubernetes-agent -n argo-rollouts
```

**Expected Output**:
```
serviceaccount/kubernetes-agent created
clusterrole.rbac.authorization.k8s.io/kubernetes-agent created
clusterrolebinding.rbac.authorization.k8s.io/kubernetes-agent created
service/kubernetes-agent created
deployment.apps/kubernetes-agent created
deployment.apps/kubernetes-agent condition met
```

#### Verify Agent Deployment

```bash
# Check pods
kubectl get pods -n argo-rollouts | grep kubernetes-agent

# Check logs
kubectl logs -n argo-rollouts deployment/kubernetes-agent --tail=50

# Test health endpoint
kubectl port-forward -n argo-rollouts svc/kubernetes-agent 8080:8080 &
curl http://localhost:8080/a2a/health
```

**Expected Output**:
```
kubernetes-agent-7d8f9c5b6d-abc12   1/1     Running   0          1m

{"status":"UP","checks":[{"name":"Kubernetes Agent","status":"UP"}]}
```

---

### 4. Install AI Plugin

The AI Plugin is a metric provider for Argo Rollouts that delegates analysis to the Kubernetes Agent.

#### Build the Plugin

```bash
# Navigate to plugin directory
cd rollouts-plugin-metric-ai

# Build the plugin binary
make build

# Verify the binary
ls -lh bin/metric-ai
```

**Expected Output**:
```
-rwxr-xr-x  1 user  staff   15M Jan 15 10:00 bin/metric-ai
```

#### Configure Argo Rollouts to Use the Plugin

```bash
# Create ConfigMap with plugin configuration
kubectl patch configmap argo-rollouts-config -n argo-rollouts --type merge -p '
{
  "data": {
    "metricProviderPlugins": "- name: \"ai\"\n  location: \"file:///plugins/metric-ai\"\n"
  }
}'

# Or create the ConfigMap if it doesn't exist
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: argo-rollouts-config
  namespace: argo-rollouts
data:
  metricProviderPlugins: |
    - name: "ai"
      location: "file:///plugins/metric-ai"
EOF
```

#### Copy Plugin to Argo Rollouts Pod

```bash
# Create a volume mount for the plugin
kubectl patch deployment argo-rollouts -n argo-rollouts --type json -p '[
  {
    "op": "add",
    "path": "/spec/template/spec/volumes/-",
    "value": {
      "name": "plugin-volume",
      "emptyDir": {}
    }
  },
  {
    "op": "add",
    "path": "/spec/template/spec/containers/0/volumeMounts/-",
    "value": {
      "name": "plugin-volume",
      "mountPath": "/plugins"
    }
  }
]'

# Copy the plugin binary
POD_NAME=$(kubectl get pods -n argo-rollouts -l app.kubernetes.io/name=argo-rollouts -o jsonpath='{.items[0].metadata.name}')
kubectl cp bin/metric-ai argo-rollouts/$POD_NAME:/plugins/metric-ai

# Make it executable
kubectl exec -n argo-rollouts $POD_NAME -- chmod +x /plugins/metric-ai

# Restart the deployment
kubectl rollout restart deployment/argo-rollouts -n argo-rollouts
kubectl rollout status deployment/argo-rollouts -n argo-rollouts
```

#### Verify Plugin Installation

```bash
# Check if plugin is loaded
kubectl logs -n argo-rollouts deployment/argo-rollouts | grep -i "plugin\|metric-ai"
```

**Expected Output**:
```
time="2024-01-15T10:00:00Z" level=info msg="Loading metric provider plugin" name=ai location="file:///plugins/metric-ai"
time="2024-01-15T10:00:00Z" level=info msg="Metric provider plugin loaded successfully" name=ai
```

---

### 5. Deploy Demo Application

The demo application is a Quarkus-based service that demonstrates AI-powered progressive delivery.

#### Deploy Using Kustomize

```bash
# Navigate to demo-app directory
cd demo-app

# Deploy all resources
kubectl apply -k kubernetes/

# Wait for rollout to be healthy
kubectl argo rollouts status demo-app -n demo-app --watch
```

**Expected Output**:
```
namespace/demo-app created
service/demo-app-stable created
service/demo-app-canary created
gateway.networking.istio.io/demo-app-gateway created
virtualservice.networking.istio.io/demo-app-vsvc created
analysistemplate.argoproj.io/ai-analysis created
rollout.argoproj.io/demo-app created

Waiting for rollout "demo-app" to be ready...
Rollout "demo-app" is healthy
```

#### Verify Deployment

```bash
# Check rollout status
kubectl get rollout -n demo-app

# Check pods
kubectl get pods -n demo-app

# Check services
kubectl get svc -n demo-app

# Check Istio resources
kubectl get gateway,virtualservice -n demo-app
```

**Expected Output**:
```
NAME       DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
demo-app   3         3         3            3           2m

NAME                           READY   STATUS    RESTARTS   AGE
demo-app-7d8f9c5b6d-abc12     2/2     Running   0          2m
demo-app-7d8f9c5b6d-def34     2/2     Running   0          2m
demo-app-7d8f9c5b6d-ghi56     2/2     Running   0          2m

NAME                   TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
demo-app-stable        ClusterIP   10.96.0.100     <none>        8080/TCP   2m
demo-app-canary        ClusterIP   10.96.0.101     <none>        8080/TCP   2m

NAME                                           AGE
gateway.networking.istio.io/demo-app-gateway   2m
virtualservice.networking.istio.io/demo-app-vsvc   2m
```

#### Access the Application

```bash
# Port forward to the stable service
kubectl port-forward -n demo-app svc/demo-app-stable 8080:8080

# Open in browser: http://localhost:8080
# Or test with curl:
curl http://localhost:8080/hello
```

**Expected Output**:
```
Hello from Argo Rollouts Demo App v1.0.0
```

---

### 6. Configure GitHub Integration

Configure the Kubernetes Agent to create pull requests when issues are detected.

#### Update AnalysisTemplate with GitHub URL

```bash
# Edit the AnalysisTemplate
kubectl edit analysistemplate ai-analysis -n demo-app

# Add the following under spec.metrics[0].provider.plugin.ai:
#   githubUrl: "https://github.com/your-org/your-repo"
#   baseBranch: "main"
```

Or apply this patch:

```bash
kubectl patch analysistemplate ai-analysis -n demo-app --type merge -p '
{
  "spec": {
    "metrics": [{
      "provider": {
        "plugin": {
          "ai": {
            "githubUrl": "https://github.com/your-org/your-repo",
            "baseBranch": "main"
          }
        }
      }
    }]
  }
}'
```

#### Verify GitHub Configuration

```bash
# Check the AnalysisTemplate
kubectl get analysistemplate ai-analysis -n demo-app -o yaml | grep -A 5 github
```

**Expected Output**:
```
githubUrl: https://github.com/your-org/your-repo
baseBranch: main
```

---

## Verification

### Complete System Check

Run these commands to verify all components are working correctly:

```bash
# 1. Check Argo Rollouts
kubectl get pods -n argo-rollouts
kubectl logs -n argo-rollouts deployment/argo-rollouts --tail=20

# 2. Check Kubernetes Agent
kubectl get pods -n argo-rollouts | grep kubernetes-agent
kubectl logs -n argo-rollouts deployment/kubernetes-agent --tail=20
curl http://localhost:8080/a2a/health  # (with port-forward active)

# 3. Check Istio
kubectl get pods -n istio-system
kubectl get gateway,virtualservice -n demo-app

# 4. Check Demo App
kubectl get rollout -n demo-app
kubectl get pods -n demo-app
kubectl argo rollouts get rollout demo-app -n demo-app

# 5. Test the application
kubectl port-forward -n demo-app svc/demo-app-stable 8080:8080 &
curl http://localhost:8080/hello
curl http://localhost:8080/q/health
```

### Test a Deployment

Trigger a canary deployment to verify the AI analysis works:

```bash
# Update the image to trigger a rollout
kubectl argo rollouts set image demo-app -n demo-app \
  demo-app=quay.io/kdubois/argo-rollouts-quarkus-demo:2.0.0

# Watch the rollout progress
kubectl argo rollouts get rollout demo-app -n demo-app --watch

# Check the AnalysisRun
kubectl get analysisrun -n demo-app
kubectl describe analysisrun -n demo-app $(kubectl get analysisrun -n demo-app -o name | tail -1)
```

**Expected Behavior**:

The rollout should progress through the canary steps (20%, 50%, 100%). At each step, an AnalysisRun should be created. The AI should analyze the deployment and return a decision (PROCEED or ROLLBACK). If successful, the rollout should complete and all pods should be updated.

---

## Configuration Reference

### Environment Variables

#### Kubernetes Agent

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOOGLE_API_KEY` | Yes* | - | Google Gemini API key |
| `OPENAI_API_KEY` | Yes* | - | OpenAI API key |
| `GITHUB_TOKEN` | Yes | - | GitHub personal access token |
| `GIT_USERNAME` | No | kubernetes-agent | Git commit username |
| `GIT_EMAIL` | No | agent@example.com | Git commit email |
| `GEMINI_MODEL` | No | gemini-2.5-flash | Gemini model name |
| `OPENAI_MODEL` | No | gpt-4o | OpenAI model name |

*Either `GOOGLE_API_KEY` or `OPENAI_API_KEY` is required.

#### AI Plugin

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `LOG_LEVEL` | No | info | Log level (debug, info, warn, error) |

### AnalysisTemplate Configuration

The AnalysisTemplate defines how the AI analyzes deployments. Key configuration options:

```yaml
spec:
  metrics:
  - name: ai-deployment-analysis
    interval: 30s              # How often to run analysis
    count: 3                   # Number of measurements
    successCondition: result.decision == "proceed"
    failureCondition: result.decision == "rollback"
    provider:
      plugin:
        ai:
          mode: "agent"        # Use agent-based analysis
          agentUrl: "http://kubernetes-agent.argo-rollouts.svc.cluster.local:8080"
          githubUrl: "https://github.com/your-org/your-repo"
          baseBranch: "main"
          extraPrompt: "Focus on error rates and latency"
```

---

## Troubleshooting

### Common Issues

#### Argo Rollouts Not Starting

**Symptoms**: Argo Rollouts pod is in CrashLoopBackOff or Error state.

**Solutions**:
```bash
# Check logs
kubectl logs -n argo-rollouts deployment/argo-rollouts

# Check events
kubectl get events -n argo-rollouts --sort-by='.lastTimestamp'

# Verify RBAC
kubectl get clusterrole argo-rollouts
kubectl get clusterrolebinding argo-rollouts

# Reinstall if necessary
kubectl delete namespace argo-rollouts
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
```

#### Kubernetes Agent Not Responding

**Symptoms**: Agent health check fails or returns errors.

**Solutions**:
```bash
# Check pod status
kubectl get pods -n argo-rollouts | grep kubernetes-agent

# Check logs for errors
kubectl logs -n argo-rollouts deployment/kubernetes-agent

# Verify secrets
kubectl get secret kubernetes-agent -n argo-rollouts -o yaml

# Check if API keys are valid
kubectl exec -n argo-rollouts deployment/kubernetes-agent -- env | grep -E "GOOGLE_API_KEY|OPENAI_API_KEY|GITHUB_TOKEN"

# Restart the agent
kubectl rollout restart deployment/kubernetes-agent -n argo-rollouts
```

#### AI Plugin Not Loaded

**Symptoms**: AnalysisRun fails with "plugin not found" error.

**Solutions**:
```bash
# Check if plugin is in the pod
POD_NAME=$(kubectl get pods -n argo-rollouts -l app.kubernetes.io/name=argo-rollouts -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n argo-rollouts $POD_NAME -- ls -la /plugins/

# Check ConfigMap
kubectl get configmap argo-rollouts-config -n argo-rollouts -o yaml

# Check logs
kubectl logs -n argo-rollouts deployment/argo-rollouts | grep -i plugin

# Recopy the plugin
kubectl cp rollouts-plugin-metric-ai/bin/metric-ai argo-rollouts/$POD_NAME:/plugins/metric-ai
kubectl exec -n argo-rollouts $POD_NAME -- chmod +x /plugins/metric-ai
kubectl rollout restart deployment/argo-rollouts -n argo-rollouts
```

#### Istio Sidecar Not Injected

**Symptoms**: Pods have only 1/1 containers instead of 2/2.

**Solutions**:
```bash
# Check namespace label
kubectl get namespace demo-app --show-labels

# Add label if missing
kubectl label namespace demo-app istio-injection=enabled

# Restart pods
kubectl rollout restart rollout/demo-app -n demo-app

# Verify injection
kubectl get pods -n demo-app -o jsonpath='{.items[*].spec.containers[*].name}'
```

#### AnalysisRun Fails Immediately

**Symptoms**: AnalysisRun shows Failed status without running measurements.

**Solutions**:
```bash
# Check AnalysisRun details
kubectl describe analysisrun -n demo-app $(kubectl get analysisrun -n demo-app -o name | tail -1)

# Check if agent is reachable from Argo Rollouts pod
POD_NAME=$(kubectl get pods -n argo-rollouts -l app.kubernetes.io/name=argo-rollouts -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n argo-rollouts $POD_NAME -- curl -s http://kubernetes-agent.argo-rollouts.svc.cluster.local:8080/a2a/health

# Check network policies
kubectl get networkpolicy -A

# Check service
kubectl get svc kubernetes-agent -n argo-rollouts
```

---

## Uninstallation

To completely remove the demo environment:

```bash
# Delete demo app
kubectl delete namespace demo-app

# Delete Kubernetes Agent
kubectl delete -k kubernetes-agent/deployment/

# Delete Argo Rollouts
kubectl delete namespace argo-rollouts

# Uninstall Istio
istioctl uninstall --purge -y
kubectl delete namespace istio-system

# Clean up CRDs
kubectl delete crd $(kubectl get crd | grep argoproj.io | awk '{print $1}')
kubectl delete crd $(kubectl get crd | grep istio.io | awk '{print $1}')
```

---

## Next Steps

After successful deployment, you can:

1. **Run the demo**: Follow the [DEMO_SCRIPT.md](DEMO_SCRIPT.md) for presentation instructions
2. **Customize the AnalysisTemplate**: Modify thresholds and metrics for your use case
3. **Build custom images**: Use the [Makefile](Makefile) to build and push your own images
4. **Explore the code**: Review the application source code and Kubernetes manifests
5. **Test failure scenarios**: Deploy buggy versions to see automatic rollback in action

---

## Additional Resources

- **Argo Rollouts Documentation**: https://argo-rollouts.readthedocs.io/
- **Istio Documentation**: https://istio.io/latest/docs/
- **AI Plugin**: https://github.com/kdubois/rollouts-plugin-metric-ai
- **Kubernetes Agent**: https://github.com/kdubois/kubernetes-aiops-agent
- **Demo Script**: [DEMO_SCRIPT.md](DEMO_SCRIPT.md)

---

**Deployment complete!** You now have a fully functional AI-powered progressive delivery environment.