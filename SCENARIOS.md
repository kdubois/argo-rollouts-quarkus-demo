# Container-Based Test Scenarios for Argo Rollouts

This document explains the simplified container-based approach for demonstrating AI-powered progressive delivery with Argo Rollouts.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [The Three Scenarios](#the-three-scenarios)
  - [Scenario 1: Stable Deployment (Happy Path)](#scenario-1-stable-deployment-happy-path)
  - [Scenario 2: NullPointerException Bug (Fixable)](#scenario-2-nullpointerexception-bug-fixable)
  - [Scenario 3: Memory Leak (Non-Fixable)](#scenario-3-memory-leak-non-fixable)
- [Quick Start](#quick-start)
- [GitOps Workflow](#gitops-workflow)
- [Demo Flow](#demo-flow)
- [Building Scenario Images](#building-scenario-images)
- [Troubleshooting](#troubleshooting)

---

## Overview

The container-based approach simplifies progressive delivery demonstrations by:

- **Built-in Load Generator**: Each container generates 50 requests/second automatically, eliminating the need for external load testing tools
- **Pre-built Scenario Images**: Three distinct container images with different behaviors (stable, null pointer bug, memory leak)
- **Automated CI/CD**: GitHub Actions workflow builds all scenario images on every push
- **GitOps-Ready**: Kustomize overlays enable easy deployment switching via git commits
- **AI-Powered Analysis**: Kubernetes agent analyzes logs and metrics to make intelligent decisions

### Why Container-Based?

1. **Easy Demos**: No need to manually trigger bugs or generate load
2. **Triggers Argo Rollouts**: Image changes automatically start progressive delivery
3. **Realistic Scenarios**: Simulates real production issues (NPE, memory leaks)
4. **Fast Analysis**: Built-in load ensures AI has enough data within 40 seconds

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         GitOps Repository                        │
│  progressive-delivery/workloads/quarkus-rollouts-demo/          │
│    ├── base/                  (Common resources)                │
│    └── overlays/                                                │
│        ├── scenario-1-stable/        (v1.stable)                │
│        ├── scenario-2-null-pointer/  (v2.nullpointer)           │
│        └── scenario-3-memory-leak/   (v3.memoryleak)            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                    ArgoCD Auto-Sync
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Argo Rollouts Controller                    │
│  Detects image change → Starts progressive delivery              │
│    Step 1: 10% canary traffic (10s)                             │
│    Step 2: 10% canary + AI analysis (40s) ← DECISION POINT      │
│    Step 3: 60% canary (20s)                                     │
│    Step 4: 100% canary (10s)                                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                    AI Analysis (40s)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Kubernetes Agent                            │
│  1. Fetches logs from stable & canary pods                      │
│  2. Analyzes with AI (GPT-4o or local LLM)                      │
│  3. Decides: PASS, FAIL, or INCONCLUSIVE                        │
│  4. Creates PR (fixable) or Issue (non-fixable)                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                    Rollout Decision
                              ↓
        ┌─────────────────────┴─────────────────────┐
        ↓                                           ↓
   ✅ PASS                                      ❌ FAIL
   Continue rollout                            Abort rollout
   (Scenario 1)                                (Scenarios 2 & 3)
```

### Components

1. **Quarkus Application** ([src/main/java/dev/kevindubois/demo/](src/main/java/dev/kevindubois/demo/))
   - `LoadGeneratorService.java`: Built-in load generator (50 req/sec)
   - `DemoScenarioService.java`: Simulates bugs (NPE, memory leak)
   - `UserResource.java`: REST endpoint with optional NPE bug

2. **Container Images** (Built by [GitHub Actions](.github/workflows/build-scenario-images.yml))
   - `v1.stable`: Healthy application
   - `v2.nullpointer`: NullPointerException bug (20% of requests)
   - `v3.memoryleak`: Memory leak (1MB per request)

3. **Kustomize Overlays** ([progressive-delivery/workloads/quarkus-rollouts-demo/](../progressive-delivery/workloads/quarkus-rollouts-demo/))
   - `base/`: Common resources (rollout, services, analysis template)
   - `overlays/scenario-X/`: Scenario-specific image tags

4. **AI Analysis** ([kubernetes-agent/](../kubernetes-agent/))
   - Analyzes logs and metrics during canary deployment
   - Creates GitHub PRs for fixable issues
   - Creates GitHub Issues for non-fixable issues

---

## The Three Scenarios

### Scenario 1: Stable Deployment (Happy Path)

**Image**: `quay.io/kevindubois/argo-rollouts-quarkus-demo:v1.stable`

**Behavior**:
- ✅ 99% success rate
- ✅ Low latency (10-50ms)
- ✅ No errors in logs
- ✅ Stable memory usage

**AI Analysis Result**: ✅ **PASS**

**Timeline**:
- 0:00 - Rollout starts, 10% traffic to canary
- 0:10 - AI analysis begins
- 0:50 - AI analysis completes: "No issues detected"
- 0:50 - Rollout continues to 60%
- 1:10 - Rollout continues to 100%
- 1:20 - **Rollout complete** ✅

**Expected Outcome**: Successful rollout, no GitHub activity

**Demo Use Case**: Show that AI correctly identifies healthy deployments

---

### Scenario 2: NullPointerException Bug (Fixable)

**Image**: `quay.io/kevindubois/argo-rollouts-quarkus-demo:v2.nullpointer`

**Behavior**:
- ❌ 20% of requests fail with NullPointerException
- ❌ Clear stack traces in logs pointing to line 39 in `UserResource.java`
- ⚠️ 80% success rate (below threshold)
- ✅ Normal latency for successful requests

**Bug Details**:
```java
// UserResource.java:39
User user = findUser(userId);
String userName = user.getName(); // NPE when user is null!
```

**AI Analysis Result**: ❌ **FAIL** (Fixable)

**Timeline**:
- 0:00 - Rollout starts, 10% traffic to canary
- 0:10 - AI analysis begins
- 0:15 - First NPE errors appear in canary logs
- 0:50 - AI analysis completes: "NullPointerException detected"
- 0:50 - **Rollout aborted** ❌
- 0:55 - AI creates **Pull Request** with fix

**GitHub Activity**:
- **Pull Request Created**: "Fix NullPointerException in UserResource"
- Contains: Root cause analysis, code fix, testing recommendations
- Branch: `fix/npe-user-resource-{timestamp}`

**Expected Outcome**: Rollout aborted, PR created with fix

**Demo Use Case**: Show AI detecting bugs with clear stack traces and creating automated fixes

---

### Scenario 3: Memory Leak (Non-Fixable)

**Image**: `quay.io/kevindubois/argo-rollouts-quarkus-demo:v3.memoryleak`

**Behavior**:
- ⚠️ Gradual performance degradation
- ⚠️ Latency increases 6x over 90 seconds (10ms → 300ms)
- ⚠️ Heap memory grows linearly (150MB → 250MB)
- ⚠️ Warning logs but **no stack traces**
- ❌ Root cause not obvious from logs alone

**Bug Details**:
```java
// DemoScenarioService.java:150
byte[] leak = new byte[1024 * 1024]; // 1MB per request
memoryLeakList.add(leak); // Never released!
```

**Log Examples**:
```
WARN  Performance degradation detected: Response times increasing. Heap usage: 50MB
ERROR CRITICAL: Severe performance degradation. Response times 3x baseline. Heap usage: 100MB
ERROR CRITICAL: Application struggling. Response times 6x baseline. Heap usage: 150MB
```

**AI Analysis Result**: ❌ **FAIL** (Non-Fixable)

**Timeline**:
- 0:00 - Rollout starts, 10% traffic to canary
- 0:10 - AI analysis begins
- 0:20 - Latency starts increasing (50ms)
- 0:30 - Warning logs appear (50MB leaked)
- 0:40 - Latency at 150ms (100MB leaked)
- 0:50 - AI analysis completes: "Memory leak suspected"
- 0:50 - **Rollout aborted** ❌
- 0:55 - AI creates **GitHub Issue**

**GitHub Activity**:
- **Issue Created**: "Memory Leak Detected - Requires Investigation"
- Contains: Symptoms, log analysis, heap dump recommendation
- Labels: `bug`, `performance`, `requires-investigation`

**Expected Outcome**: Rollout aborted, Issue created (no automated fix)

**Demo Use Case**: Show AI detecting complex issues without obvious stack traces and escalating to humans

---

## Quick Start

### Prerequisites

- OpenShift cluster with Argo Rollouts and ArgoCD installed
- Kubernetes agent deployed ([kubernetes-agent/deployment/](../kubernetes-agent/deployment/))
- GitHub token configured for PR/Issue creation

### Deploy a Scenario

**Scenario 1 (Stable)**:
```bash
kubectl apply -k progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-1-stable/
```

**Scenario 2 (NullPointerException)**:
```bash
kubectl apply -k progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-2-null-pointer/
```

**Scenario 3 (Memory Leak)**:
```bash
kubectl apply -k progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-3-memory-leak/
```

### Watch the Rollout

```bash
# Watch rollout progress
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# Check AI analysis
kubectl get analysisrun -n quarkus-demo

# View analysis details
kubectl describe analysisrun <analysis-run-name> -n quarkus-demo

# Check canary logs
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=canary --tail=50 -f

# Check stable logs
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=stable --tail=50 -f
```

### Access the Application

```bash
# Get the route URL
oc get route quarkus-demo -n quarkus-demo

# Test the endpoint
curl https://quarkus-demo-quarkus-demo.apps.your-cluster.com/api/status
```

---

## GitOps Workflow

The GitOps workflow enables triggering rollouts via git commits:

### 1. Edit Kustomize Overlay

Choose the scenario you want to deploy:

```bash
# Edit the rollout patch to change the image
vim progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-2-null-pointer/rollout-patch.yaml
```

Example change:
```yaml
spec:
  template:
    spec:
      containers:
      - name: quarkus-demo
        image: quay.io/kevindubois/argo-rollouts-quarkus-demo:v2.nullpointer  # Changed from v1.stable
```

### 2. Commit and Push

```bash
git add progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-2-null-pointer/
git commit -m "Deploy scenario 2: NullPointerException bug"
git push origin main
```

### 3. ArgoCD Auto-Sync

ArgoCD detects the change and syncs automatically (if auto-sync is enabled):

```bash
# Check ArgoCD application status
argocd app get quarkus-rollouts-demo

# Manual sync if needed
argocd app sync quarkus-rollouts-demo
```

### 4. Argo Rollouts Triggers

Argo Rollouts detects the image change and starts progressive delivery:

```bash
# Watch the rollout
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

### 5. AI Analysis

After 10 seconds at 10% canary traffic, AI analysis begins:

```bash
# Watch analysis progress
kubectl get analysisrun -n quarkus-demo -w

# View analysis logs
kubectl logs -n quarkus-demo -l app=kubernetes-agent -f
```

### 6. Decision Point

At 50 seconds, AI makes a decision:
- ✅ **PASS**: Rollout continues to 60% → 100%
- ❌ **FAIL**: Rollout aborted, GitHub PR/Issue created

---

## Demo Flow

### Complete Demo Script (All Three Scenarios)

**Total Time**: ~6 minutes

#### Part 1: Scenario 1 - Happy Path (2 minutes)

```bash
# 1. Deploy stable version
kubectl apply -k progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-1-stable/

# 2. Watch rollout (in separate terminal)
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# 3. Explain what's happening:
# - 10% traffic to canary (10s)
# - AI analysis starts (40s)
# - AI finds no issues
# - Rollout continues to 60%, then 100%

# 4. Show AI analysis result
kubectl get analysisrun -n quarkus-demo
kubectl describe analysisrun <name> -n quarkus-demo

# Expected: Phase: Successful, Message: "No issues detected"
```

#### Part 2: Scenario 2 - NullPointerException (1.5 minutes)

```bash
# 1. Deploy buggy version
kubectl apply -k progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-2-null-pointer/

# 2. Watch rollout
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# 3. Show canary logs with NPE errors
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=canary --tail=20

# Expected: NullPointerException at UserResource.java:39

# 4. Wait for AI analysis (40s)
kubectl get analysisrun -n quarkus-demo -w

# 5. Show rollout aborted
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo

# Expected: Status: Degraded, Message: "Analysis Failed"

# 6. Check GitHub for PR
# Expected: PR created with fix for NullPointerException
```

#### Part 3: Scenario 3 - Memory Leak (2 minutes)

```bash
# 1. Deploy memory leak version
kubectl apply -k progressive-delivery/workloads/quarkus-rollouts-demo/overlays/scenario-3-memory-leak/

# 2. Watch rollout
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# 3. Show canary logs with performance warnings
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=canary --tail=30 -f

# Expected: 
# - "Performance degradation detected: Heap usage: 50MB"
# - "CRITICAL: Severe performance degradation. Heap usage: 100MB"
# - "CRITICAL: Application struggling. Heap usage: 150MB"

# 4. Wait for AI analysis (40s)
kubectl get analysisrun -n quarkus-demo -w

# 5. Show rollout aborted
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo

# Expected: Status: Degraded, Message: "Analysis Failed"

# 6. Check GitHub for Issue
# Expected: Issue created recommending heap dump analysis
```

### Key Demo Points

1. **Built-in Load**: No need to run external load tests
2. **Fast Analysis**: AI completes analysis in 40 seconds
3. **Smart Decisions**: AI distinguishes between fixable (PR) and non-fixable (Issue) bugs
4. **Automatic Remediation**: PRs include code fixes, Issues include investigation steps
5. **GitOps-Driven**: All changes via git commits, no manual kubectl commands needed

---

## Building Scenario Images

Scenario images are built automatically by GitHub Actions on every push to `main`.

### GitHub Actions Workflow

File: [`.github/workflows/build-scenario-images.yml`](../.github/workflows/build-scenario-images.yml)

**Triggers**:
- Push to `main` branch with changes in `argo-rollouts-quarkus-demo/**`
- Manual workflow dispatch

**Matrix Strategy**:
```yaml
matrix:
  scenario:
    - name: stable
      tag: v1.stable
      null_pointer: "false"
      memory_leak: "false"
    - name: null-pointer-bug
      tag: v2.nullpointer
      null_pointer: "true"
      memory_leak: "false"
    - name: memory-leak
      tag: v3.memoryleak
      null_pointer: "false"
      memory_leak: "true"
```

**Build Process**:
1. Checkout code
2. Set up JDK 25
3. Update `application.properties` with scenario-specific flags
4. Build with Maven (`./mvnw clean package`)
5. Build Docker image with `Dockerfile.jvm`
6. Push to Quay.io with two tags:
   - Version tag (e.g., `v1.stable`)
   - Latest tag (e.g., `stable-latest`)

### Manual Build

To build images locally:

```bash
cd argo-rollouts-quarkus-demo

# Build Scenario 1 (Stable)
./mvnw clean package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm \
  -t quay.io/kevindubois/argo-rollouts-quarkus-demo:v1.stable .

# Build Scenario 2 (NullPointerException)
# First, edit src/main/resources/application.properties:
# enable.null.pointer.bug=true
./mvnw clean package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm \
  -t quay.io/kevindubois/argo-rollouts-quarkus-demo:v2.nullpointer .

# Build Scenario 3 (Memory Leak)
# Edit src/main/resources/application.properties:
# enable.memory.leak=true
./mvnw clean package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm \
  -t quay.io/kevindubois/argo-rollouts-quarkus-demo:v3.memoryleak .
```

### Image Registry

Images are hosted on Quay.io:
- Repository: `quay.io/kevindubois/argo-rollouts-quarkus-demo`
- Public visibility
- Tags: `v1.stable`, `v2.nullpointer`, `v3.memoryleak`

---

## Troubleshooting

### Rollout Not Starting

**Symptom**: Rollout stays in "Healthy" state after applying new overlay

**Causes**:
1. Image tag hasn't changed
2. ArgoCD hasn't synced yet
3. Rollout controller not running

**Solutions**:
```bash
# Check if image changed
kubectl get rollout quarkus-demo -n quarkus-demo -o yaml | grep image:

# Force ArgoCD sync
argocd app sync quarkus-rollouts-demo

# Check rollout controller
kubectl get pods -n argo-rollouts
```

### AI Analysis Stuck

**Symptom**: AnalysisRun stays in "Running" state for more than 60 seconds

**Causes**:
1. Kubernetes agent not responding
2. AI API rate limit exceeded
3. Network connectivity issues

**Solutions**:
```bash
# Check kubernetes-agent logs
kubectl logs -n quarkus-demo -l app=kubernetes-agent --tail=50

# Check analysis run status
kubectl describe analysisrun <name> -n quarkus-demo

# Restart kubernetes-agent
kubectl rollout restart deployment kubernetes-agent -n quarkus-demo
```

### No GitHub PR/Issue Created

**Symptom**: Rollout aborted but no GitHub activity

**Causes**:
1. GitHub token not configured
2. Repository URL incorrect
3. Insufficient permissions

**Solutions**:
```bash
# Check kubernetes-agent configuration
kubectl get secret -n quarkus-demo kubernetes-agent-secret -o yaml

# Check agent logs for GitHub errors
kubectl logs -n quarkus-demo -l app=kubernetes-agent | grep -i github

# Verify GitHub token has correct permissions:
# - repo (full control)
# - workflow (if modifying workflows)
```

### Load Generator Not Working

**Symptom**: No traffic to canary pods

**Causes**:
1. Load generator disabled in configuration
2. Pods not ready
3. Service misconfiguration

**Solutions**:
```bash
# Check load generator status in logs
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=canary | grep "Load generator"

# Expected: "Load generator started - generating 50 requests/second"

# Check pod readiness
kubectl get pods -n quarkus-demo -l app=quarkus-demo

# Check service endpoints
kubectl get endpoints -n quarkus-demo
```

### Memory Leak Not Triggering

**Symptom**: Scenario 3 doesn't show performance degradation

**Causes**:
1. Wrong image deployed
2. Not enough time elapsed
3. Insufficient load

**Solutions**:
```bash
# Verify correct image
kubectl get rollout quarkus-demo -n quarkus-demo -o yaml | grep image:
# Expected: v3.memoryleak

# Check memory usage
kubectl top pods -n quarkus-demo -l app=quarkus-demo,role=canary

# Wait at least 60 seconds for memory to accumulate
# Check logs for memory warnings
kubectl logs -n quarkus-demo -l app=quarkus-demo,role=canary | grep -i "heap usage"
```

### Rollout Aborted Too Early

**Symptom**: Rollout aborted before AI analysis completes

**Causes**:
1. Analysis template misconfigured
2. Timeout too short
3. Metrics provider error

**Solutions**:
```bash
# Check analysis template
kubectl get analysistemplate ai-analysis-agent -n quarkus-demo -o yaml

# Verify analysis timing in rollout
kubectl get rollout quarkus-demo -n quarkus-demo -o yaml | grep -A 5 "analysis:"

# Expected: startingStep: 2 (starts after 10s pause)
# Expected: pause: { duration: 40s } (enough time for AI)
```

---

## Additional Resources

- **Main README**: [README.md](README.md) - Application overview
- **Deployment Guide**: [DEPLOYMENT.md](DEPLOYMENT.md) - Detailed deployment instructions
- **Bug Scenarios**: [BUG_SCENARIOS.md](BUG_SCENARIOS.md) - Technical details of bug implementations
- **Kubernetes Agent**: [kubernetes-agent/README.md](../kubernetes-agent/README.md) - AI agent documentation
- **Progressive Delivery**: [progressive-delivery/README.md](../progressive-delivery/README.md) - GitOps structure

---

## Summary

The container-based approach provides:

✅ **Simplified Demos**: No external tools needed, everything self-contained  
✅ **Fast Analysis**: Built-in load ensures AI has data within 40 seconds  
✅ **Realistic Scenarios**: Three distinct behaviors (stable, NPE, memory leak)  
✅ **Automated CI/CD**: GitHub Actions builds all images automatically  
✅ **GitOps-Ready**: Kustomize overlays enable easy scenario switching  
✅ **Smart Remediation**: AI creates PRs for fixable bugs, Issues for complex problems  

**Next Steps**: Try the [Demo Flow](#demo-flow) to see all three scenarios in action!

---