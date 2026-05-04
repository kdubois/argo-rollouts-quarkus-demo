# Argo Rollouts AI Plugin - Conference Demo Script

**Conference Presentation Guide**: AI-Powered Progressive Delivery with Argo Rollouts

## Table of Contents

- [Introduction](#introduction)
- [Demo Setup](#demo-setup)
- [Happy Path Demo](#happy-path-demo)
- [Failure Scenario Demo](#failure-scenario-demo)
- [Q&A Talking Points](#qa-talking-points)
- [Reset Instructions](#reset-instructions)
- [Troubleshooting](#troubleshooting)

---

## Introduction

**Duration**: 3-5 minutes

### Opening Hook

"How many of you have experienced a production incident caused by a bad deployment? Now, what if I told you that AI could analyze your canary deployments in real-time, identify issues before they impact users, and even create pull requests to fix the problems automatically?"

### Key Message

Progressive delivery is critical for safe deployments, but analyzing metrics and logs manually is time-consuming and error-prone. The Argo Rollouts AI Plugin brings autonomous AI agents into your deployment pipeline to analyze canary deployments using real Kubernetes logs and metrics, identify issues before they reach production, make decisions to promote or rollback automatically, and create pull requests with fixes when problems are detected.

### Architecture Overview

```
┌─────────────────┐
│   Demo App      │ ← Quarkus application with metrics
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Argo Rollouts   │ ← Progressive delivery controller
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  AI Plugin      │ ← Metric provider plugin
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Kubernetes      │ ← Autonomous AI agent
│    Agent        │   (Gemini/OpenAI powered)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    GitHub       │ ← Automated PR creation
└─────────────────┘
```

**Talking Points**:

The system requires no manual intervention as the AI makes deployment decisions autonomously. It analyzes real Kubernetes data including logs, events, and metrics. When issues are detected, the system performs autonomous remediation by creating pull requests automatically. This is production-ready technology built on Argo Rollouts, a CNCF graduated project. The entire solution is open source and available today.

---

## Demo Setup

### Pre-Demo Checklist

Before starting the demo, verify that your Kubernetes cluster is running and accessible. Ensure that Argo Rollouts is installed and healthy. Confirm that the AI Plugin is configured in the argo-rollouts ConfigMap. Check that the Kubernetes Agent is deployed and responding to health checks. Verify that the demo app is deployed in the `demo-app` namespace. Confirm that Istio service mesh is configured correctly. Ensure your GitHub token is configured for PR creation.

Prepare your terminal windows as follows:
- **Window 1**: Watch rollout status continuously
- **Window 2**: Watch pod status with updates every 2 seconds
- **Window 3**: Execute demo commands
- **Window 4**: View logs (optional, for deep dive)

### Quick Verification

```bash
# Verify cluster access
kubectl cluster-info

# Check Argo Rollouts
kubectl get pods -n argo-rollouts

# Check Kubernetes Agent
kubectl get pods -n argo-rollouts | grep kubernetes-agent

# Check demo app
kubectl get rollout -n demo-app
kubectl get pods -n demo-app
```

### Terminal Setup

**Terminal 1 - Rollout Status** (keep visible):
```bash
kubectl argo rollouts get rollout demo-app -n demo-app --watch
```

**Terminal 2 - Pod Status** (keep visible):
```bash
watch -n 2 'kubectl get pods -n demo-app -o wide'
```

**Terminal 3 - Commands** (for executing demo steps)

**Terminal 4 - Logs** (optional, for deep dive):
```bash
kubectl logs -f -n argo-rollouts deployment/kubernetes-agent
```

---

## Happy Path Demo

**Duration**: 8-10 minutes

### Scenario Overview

"Let's start with a successful deployment. We'll deploy a new version of our application and watch as the AI analyzes the canary deployment in real-time."

### Step 1: Show Current State

**Talking Points**:

"Here's our application running in production with 3 replicas. All pods are healthy and serving traffic to users."

```bash
# Show current deployment
kubectl get rollout demo-app -n demo-app

# Show current pods
kubectl get pods -n demo-app -l app=demo-app

# Access the application
kubectl port-forward -n demo-app svc/demo-app-stable 8080:8080
# Open browser: http://localhost:8080
```

**Expected Output**:
```
NAME       DESIRED   CURRENT   UP-TO-DATE   AVAILABLE
demo-app   3         3         3            3

NAME                           READY   STATUS    RESTARTS   AGE
demo-app-7d8f9c5b6d-abc12     2/2     Running   0          5m
demo-app-7d8f9c5b6d-def34     2/2     Running   0          5m
demo-app-7d8f9c5b6d-ghi56     2/2     Running   0          5m
```

### Step 2: Trigger Canary Deployment

**Talking Points**:

"Now let's deploy version 2.0.0 with a new feature. Argo Rollouts will automatically create a canary deployment. The AI plugin will analyze the canary at each step of the rollout process."

```bash
# Update the image to trigger a new rollout
kubectl argo rollouts set image demo-app -n demo-app \
  demo-app=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable

# Or use kubectl set image directly
kubectl set image rollout/demo-app demo-app=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable -n demo-app
```

**Expected Output**:
```
rollout "demo-app" image updated
```

### Step 3: Watch Canary Progression

**Talking Points**:

"Notice the rollout strategy progresses through three stages: 20%, 50%, and finally 100% traffic. At each step, the AI analyzes logs and metrics from both the stable and canary versions. The analysis runs every 30 seconds for 3 iterations to ensure we have enough data to make an informed decision."

**Watch Terminal 1** - You should see:
```
Name:            demo-app
Namespace:       demo-app
Status:          ॥ Paused
Strategy:        Canary
  Step:          1/6
  SetWeight:     20
  ActualWeight:  20
Images:          ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable (canary)
                 ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable (stable)
Replicas:
  Desired:       3
  Current:       4
  Updated:       1
  Ready:         4
  Available:     4

NAME                                    KIND         STATUS        AGE  INFO
⟳ demo-app                              Rollout      ॥ Paused      10m  
├──# revision:2                                                         
│  ├──⧉ demo-app-789abc123              ReplicaSet   ✔ Healthy     30s  canary
│  │  └──□ demo-app-789abc123-xyz12    Pod          ✔ Running     30s  ready:2/2
│  └──α demo-app-789abc123-2           AnalysisRun  ◌ Running     20s  ✔ 1,◌ 1
└──# revision:1                                                         
   └──⧉ demo-app-7d8f9c5b6d             ReplicaSet   ✔ Healthy     10m  stable
      ├──□ demo-app-7d8f9c5b6d-abc12   Pod          ✔ Running     10m  ready:2/2
      ├──□ demo-app-7d8f9c5b6d-def34   Pod          ✔ Running     10m  ready:2/2
      └──□ demo-app-7d8f9c5b6d-ghi56   Pod          ✔ Running     10m  ready:2/2
```

### Step 4: Show AI Analysis

**Talking Points**:

"Let's look at what the AI is analyzing behind the scenes. It's collecting logs from both stable and canary pods. The Kubernetes Agent uses its built-in tools to gather metrics and events from the cluster. It then uses a large language model to analyze this data and make an informed decision about whether to proceed with the deployment."

```bash
# View the AnalysisRun
kubectl get analysisrun -n demo-app

# Get detailed analysis results
kubectl describe analysisrun -n demo-app $(kubectl get analysisrun -n demo-app -o name | tail -1)

# View AI plugin logs
kubectl logs -n argo-rollouts deployment/argo-rollouts | grep -A 10 "AI metric"

# View Kubernetes Agent logs
kubectl logs -n argo-rollouts deployment/kubernetes-agent | tail -50
```

**Expected Output** (AnalysisRun):
```
Name:         demo-app-789abc123-2
Namespace:    demo-app
Status:       Running
Phase:        Running

Metrics:
  Name:              ai-deployment-analysis
  Successful:        1
  Inconclusive:      1
  Failed:            0
  
  Measurements:
    Phase:           Successful
    Message:         Canary deployment is healthy
                     - Success rate: 99.8%
                     - Error rate: 0.2%
                     - P95 latency: 145ms
                     - No critical issues detected
                     Decision: PROCEED
```

### Step 5: Successful Promotion

**Talking Points**:

"The AI has analyzed the canary at both 20% and 50% traffic levels. All metrics are healthy with no issues detected. Based on this analysis, Argo Rollouts automatically promotes the canary to 100% traffic. The old version is scaled down and the new version becomes the stable release."

**Watch Terminal 1** - Final state:
```
Name:            demo-app
Namespace:       demo-app
Status:          ✔ Healthy
Strategy:        Canary
Images:          ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable (stable)
Replicas:
  Desired:       3
  Current:       3
  Updated:       3
  Ready:         3
  Available:     3

NAME                                    KIND         STATUS        AGE  INFO
⟳ demo-app                              Rollout      ✔ Healthy     12m  
├──# revision:2                                                         
│  ├──⧉ demo-app-789abc123              ReplicaSet   ✔ Healthy     2m   stable
│  │  ├──□ demo-app-789abc123-xyz12    Pod          ✔ Running     2m   ready:2/2
│  │  ├──□ demo-app-789abc123-abc34    Pod          ✔ Running     1m   ready:2/2
│  │  └──□ demo-app-789abc123-def56    Pod          ✔ Running     1m   ready:2/2
│  └──α demo-app-789abc123-2           AnalysisRun  ✔ Successful  2m   ✔ 3
└──# revision:1                                                         
   └──⧉ demo-app-7d8f9c5b6d             ReplicaSet   • ScaledDown  12m  
```

**Key Takeaway**:

"In just 2 minutes, we deployed a new version with AI-powered analysis at each step. There was no manual intervention and no guesswork. This is autonomous, intelligent progressive delivery in action."

---

## Failure Scenario Demo

**Duration**: 10-12 minutes

### Scenario Overview

"Now let's see what happens when something goes wrong. We'll deploy a version with a bug and watch the AI detect the issue and automatically rollback the deployment."

### Step 1: Deploy Buggy Version

**Talking Points**:

"This version has a bug that causes increased error rates. The AI will detect this during canary analysis. It will automatically rollback the deployment and create a GitHub pull request with the fix."

```bash
# Deploy buggy version with NullPointerException
kubectl set image rollout/demo-app demo-app=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer -n demo-app

# Or use kubectl argo rollouts
kubectl argo rollouts set image demo-app -n demo-app \
  demo-app=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer
```

### Step 2: Watch Canary Fail

**Talking Points**:

"The canary is deployed at 20% traffic. The AI starts analyzing logs and metrics immediately. Notice the error rate increasing in the canary pods compared to the stable version."

**Watch Terminal 1**:
```
Name:            demo-app
Namespace:       demo-app
Status:          ॥ Paused
Strategy:        Canary
  Step:          1/6
  SetWeight:     20
  ActualWeight:  20
Images:          ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer (canary)
                 ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable (stable)

NAME                                    KIND         STATUS        AGE  INFO
⟳ demo-app                              Rollout      ॥ Paused      15m  
├──# revision:3                                                         
│  ├──⧉ demo-app-456def789              ReplicaSet   ✔ Healthy     45s  canary
│  │  └──□ demo-app-456def789-abc12    Pod          ✔ Running     45s  ready:2/2
│  └──α demo-app-456def789-3           AnalysisRun  ◌ Running     35s  ◌ 1
└──# revision:2                                                         
   └──⧉ demo-app-789abc123              ReplicaSet   ✔ Healthy     5m   stable
```

### Step 3: Show AI Detection

**Talking Points**:

"The AI has detected elevated error rates in the canary deployment. It's analyzing the logs to identify the root cause of the problem. Based on this analysis, the AI makes a decision to rollback the deployment."

```bash
# View the failing AnalysisRun
kubectl describe analysisrun -n demo-app $(kubectl get analysisrun -n demo-app -o name | tail -1)

# View canary pod logs (showing errors)
kubectl logs -n demo-app -l role=canary --tail=50
```

**Expected Output** (AnalysisRun):
```
Name:         demo-app-456def789-3
Namespace:    demo-app
Status:       Failed
Phase:        Failed

Metrics:
  Name:              ai-deployment-analysis
  Successful:        0
  Inconclusive:      1
  Failed:            1
  
  Measurements:
    Phase:           Failed
    Message:         Canary deployment has critical issues
                     
                     Root Cause Analysis:
                     The error rate is 15.3%, which exceeds the threshold of 5%.
                     The success rate is 84.7%, which is below the threshold of 95%.
                     A pattern of NullPointerException was detected in the request handler.
                     This is causing 15% of requests to fail with 500 errors.
                     
                     Recommendation: ROLLBACK
                     
                     GitHub PR created: https://github.com/owner/repo/pull/456
                     
                     Suggested Fix:
                     Add null check in UserService.getUser() method.
                     Add defensive programming for edge cases.
                     Increase test coverage for error scenarios.
```

### Step 4: Automatic Rollback

**Talking Points**:

"Argo Rollouts automatically aborts the deployment based on the AI's decision. Traffic is shifted back to the stable version. Notice that only 20% of traffic saw the bug, so user impact was minimal."

**Watch Terminal 1**:
```
Name:            demo-app
Namespace:       demo-app
Status:          ✖ Degraded
Message:         RolloutAborted: Metric "ai-deployment-analysis" assessed Failed
Strategy:        Canary
Images:          ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable (stable)
Replicas:
  Desired:       3
  Current:       3
  Updated:       0
  Ready:         3
  Available:     3

NAME                                    KIND         STATUS        AGE  INFO
⟳ demo-app                              Rollout      ✖ Degraded    16m  
├──# revision:3                                                         
│  ├──⧉ demo-app-456def789              ReplicaSet   • ScaledDown  2m   canary
│  └──α demo-app-456def789-3           AnalysisRun  ✖ Failed      2m   ✖ 1
└──# revision:2                                                         
   └──⧉ demo-app-789abc123              ReplicaSet   ✔ Healthy     6m   stable
      ├──□ demo-app-789abc123-xyz12    Pod          ✔ Running     6m   ready:2/2
      ├──□ demo-app-789abc123-abc34    Pod          ✔ Running     5m   ready:2/2
      └──□ demo-app-789abc123-def56    Pod          ✔ Running     5m   ready:2/2
```

### Step 5: Show GitHub PR

**Talking Points**:

"The Kubernetes Agent created a pull request with the fix. It includes a detailed root cause analysis and the proposed code changes. Developers can review and merge the fix, then trigger a new deployment."

```bash
# View agent logs showing PR creation
kubectl logs -n argo-rollouts deployment/kubernetes-agent | grep -A 20 "Creating GitHub PR"
```

**Show in Browser**:

Open the GitHub PR link from the AnalysisRun. Show the PR description which includes the root cause analysis, proposed code fix, testing recommendations, and links to the relevant Kubernetes resources.

**Key Takeaway**:

"The AI detected the issue in under 2 minutes, automatically rolled back the deployment, and created a pull request with the fix. This is autonomous progressive delivery in action."

---

## Q&A Talking Points

### Common Questions

#### "How does the AI make decisions?"

The Kubernetes Agent uses large language models like Gemini or OpenAI with structured outputs. It analyzes real logs, metrics, and events from Kubernetes. The system uses predefined thresholds and patterns from the AnalysisTemplate. Decisions are based on success rate, error rate, latency, and trends over time. The agent returns structured JSON with a decision (PROCEED, ROLLBACK, or PAUSE) along with detailed reasoning.

#### "What if the AI makes a wrong decision?"

The system is designed with safety in mind. Canary deployments limit the blast radius by gradually increasing traffic from 20% to 50% to 100%. Multiple analysis iterations run before promotion to ensure consistency. Human review of GitHub pull requests is required before merging fixes. Rollback is automatic and fast when issues are detected. All decisions are logged and auditable for post-incident analysis.

#### "Can I customize the AI analysis?"

Yes, the AnalysisTemplate is fully customizable. You can define your own metrics and thresholds. You can add custom Prometheus queries for your specific application. You can provide specific instructions to the AI about what to focus on. The `extraPrompt` parameter allows you to add additional context. You can configure the analysis frequency and iteration count to match your needs.

#### "What about cost and latency?"

The system uses efficient models like Gemini Flash or GPT-4o-mini. Each analysis costs approximately $0.01. Analysis only runs during deployments, not continuously. The latency for each analysis is 5-15 seconds. Analysis runs in parallel with traffic ramping, so it doesn't block deployment progression. The intervals are configurable with a default of 30 seconds.

#### "Does it work with other service meshes?"

The demo shows Istio, which is currently supported. Argo Rollouts also supports Linkerd, AWS App Mesh, Traefik, and NGINX. The AI plugin works with any traffic routing mechanism supported by Argo Rollouts. The plugin focuses on analyzing application metrics and logs, which are independent of the service mesh.

#### "How do I get started?"

First, install Argo Rollouts, which is a CNCF graduated project. Then deploy the AI plugin, which is open source. Next, deploy the Kubernetes Agent, also open source. Configure your AnalysisTemplate with your metrics and thresholds. Finally, start deploying with AI-powered analysis. See the DEPLOYMENT.md file for detailed step-by-step instructions.

---

## Reset Instructions

### Between Demos

To reset the demo app to a clean state between presentations:

```bash
# Delete the rollout
kubectl delete rollout demo-app -n demo-app

# Wait for pods to terminate
kubectl wait --for=delete pod -l app=demo-app -n demo-app --timeout=60s

# Redeploy from manifests
kubectl apply -k demo-app/kubernetes/

# Wait for rollout to be healthy
kubectl argo rollouts status demo-app -n demo-app --watch

# Verify the state
kubectl get rollout demo-app -n demo-app
kubectl get pods -n demo-app
```

### Quick Reset (Keep Rollout)

If you just want to reset to a known good state without recreating everything:

```bash
# Abort any in-progress rollout
kubectl argo rollouts abort demo-app -n demo-app

# Promote to stable
kubectl argo rollouts promote demo-app -n demo-app --full

# Reset to stable version
kubectl argo rollouts set image demo-app -n demo-app \
  demo-app=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable

# Or use kubectl set image
kubectl set image rollout/demo-app demo-app=ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable -n demo-app
```

### Clean Up Analysis Runs

```bash
# Delete old AnalysisRuns
kubectl delete analysisrun -n demo-app --all

# Or keep only recent ones
kubectl delete analysisrun -n demo-app \
  $(kubectl get analysisrun -n demo-app -o name | head -n -3)
```

---

## Troubleshooting

### Demo Not Working

#### Rollout Stuck in Paused State

**Symptoms**: The rollout shows "Paused" status but doesn't progress to the next step.

**Solutions**:
```bash
# Check if analysis is running
kubectl get analysisrun -n demo-app

# Check AI plugin logs
kubectl logs -n argo-rollouts deployment/argo-rollouts | grep -i "metric-ai"

# Check Kubernetes Agent health
kubectl get pods -n argo-rollouts | grep kubernetes-agent
kubectl logs -n argo-rollouts deployment/kubernetes-agent | tail -50

# Manual promotion if needed
kubectl argo rollouts promote demo-app -n demo-app
```

#### AnalysisRun Failing

**Symptoms**: The AnalysisRun shows "Error" or "Inconclusive" status.

**Solutions**:
```bash
# Check AnalysisRun details
kubectl describe analysisrun -n demo-app $(kubectl get analysisrun -n demo-app -o name | tail -1)

# Check if agent is reachable
kubectl exec -n argo-rollouts deployment/argo-rollouts -- \
  curl -s http://kubernetes-agent.argo-rollouts.svc.cluster.local:8080/a2a/health

# Check agent logs for errors
kubectl logs -n argo-rollouts deployment/kubernetes-agent | grep -i error

# Verify API keys are configured
kubectl get secret kubernetes-agent -n argo-rollouts -o yaml
```

#### No Canary Pods Created

**Symptoms**: The rollout updates but no canary pods appear in the cluster.

**Solutions**:
```bash
# Check rollout strategy
kubectl get rollout demo-app -n demo-app -o yaml | grep -A 20 strategy

# Check for resource constraints
kubectl describe nodes | grep -A 5 "Allocated resources"

# Check events
kubectl get events -n demo-app --sort-by='.lastTimestamp' | tail -20

# Verify service mesh injection
kubectl get pods -n demo-app -o yaml | grep "sidecar.istio.io/inject"
```

### Presentation Issues

#### Terminal Not Updating

**Solution**:
```bash
# Restart watch commands
# Terminal 1:
kubectl argo rollouts get rollout demo-app -n demo-app --watch

# Terminal 2:
watch -n 2 'kubectl get pods -n demo-app -o wide'
```

#### Can't Access Application

**Solution**:
```bash
# Check services
kubectl get svc -n demo-app

# Port forward to stable service
kubectl port-forward -n demo-app svc/demo-app-stable 8080:8080

# Or use Istio gateway
kubectl get gateway -n demo-app
kubectl get virtualservice -n demo-app
```

#### GitHub PR Not Created

**Solution**:
```bash
# Check if GitHub token is configured
kubectl get secret kubernetes-agent -n argo-rollouts -o jsonpath='{.data.GITHUB_TOKEN}' | base64 -d

# Check agent logs for GitHub errors
kubectl logs -n argo-rollouts deployment/kubernetes-agent | grep -i github

# Verify GitHub URL in AnalysisTemplate
kubectl get analysistemplate ai-analysis -n demo-app -o yaml | grep githubUrl
```

### Emergency Procedures

#### Demo Completely Broken

If the demo is completely broken and you need to start fresh:

```bash
# Delete and recreate everything
kubectl delete namespace demo-app
kubectl create namespace demo-app
kubectl apply -k demo-app/kubernetes/

# Wait for healthy state
kubectl wait --for=condition=available --timeout=300s \
  rollout/demo-app -n demo-app
```

#### Need to Skip AI Analysis

If you need to bypass the AI analysis temporarily:

```bash
# Temporarily disable analysis
kubectl patch rollout demo-app -n demo-app --type merge -p '
{
  "spec": {
    "strategy": {
      "canary": {
        "analysis": null
      }
    }
  }
}'

# Manual promotion
kubectl argo rollouts promote demo-app -n demo-app --full
```

---

## Presentation Tips

### Timing

The introduction should take 3-5 minutes. The happy path demo should take 8-10 minutes. The failure scenario should take 10-12 minutes. Reserve 5-10 minutes for Q&A. The total presentation should be 25-35 minutes.

### Engagement

Ask the audience questions throughout the presentation. Show enthusiasm about the intersection of AI and Kubernetes. Relate the demo to real-world scenarios that the audience has experienced. Emphasize the safety and automation aspects. Highlight that this is open source technology available today.

### Backup Plans

Have screenshots ready if the live demo fails. Prepare a video recording as a fallback option. Keep reset commands easily accessible. Have the troubleshooting guide open in a browser tab.

### Key Messages to Reinforce

The AI makes deployment decisions, not just monitoring. The system performs autonomous remediation by creating pull requests automatically. This is production-ready technology built on a CNCF graduated project. The entire solution is open source and available today on GitHub. The system is safe by design with canary deployments and automatic rollback.

---

## Success Criteria

Your demo is successful if the audience understands how AI analyzes deployments, sees the value of autonomous progressive delivery, knows how to get started with the plugin, asks thoughtful questions about implementation, and leaves excited about AI and Kubernetes.

---

## Additional Resources

- **Demo App Repository**: https://github.com/kdubois/argo-rollouts-quarkus-demo
- **Deployment Guide**: [DEPLOYMENT.md](DEPLOYMENT.md)
- **Argo Rollouts Docs**: https://argo-rollouts.readthedocs.io/
- **AI Plugin**: https://github.com/kdubois/rollouts-plugin-metric-ai
- **Kubernetes Agent**: https://github.com/kdubois/kubernetes-aiops-agent

---

**Good luck with your presentation!**

*Remember: The goal is to inspire the audience about the future of AI-powered deployments, not to show every technical detail.*