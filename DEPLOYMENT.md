# Deployment guide

The demo application is not deployed from this repository. The rollout, AnalysisTemplate, services, route, and RBAC live in the [progressive-delivery](../progressive-delivery) repository, and Argo CD syncs them into the `quarkus-demo` namespace.

This guide covers the local options and points at the full install path.

## Deploy the full stack

Follow the [progressive-delivery README](../progressive-delivery/README.md). It bootstraps OpenShift GitOps, Argo Rollouts with the AI plugin, the agent, and this application. The short version:

```bash
git clone https://github.com/kdubois/progressive-delivery
cd progressive-delivery
./bootstrap/bootstrap.java
```

The bootstrap prompts for an AI API key and a GitHub token, installs the operators, and hands the rest to Argo CD. On an existing Argo CD instance use `--overlay existing-argocd`.

The deployment needs:

- OpenShift 4.20 or later, or the vanilla Kubernetes equivalents listed in the progressive-delivery README
- An OpenAI-compatible model endpoint and key
- A GitHub token with `repo` scope if you want the remediation agent to open PRs and issues

## Check the deployment

```bash
# Argo CD synced the application
oc get application quarkus-rollouts-demo -n openshift-gitops

# Rollout and pods
oc get rollout quarkus-demo -n quarkus-demo
oc get pods -n quarkus-demo

# Dashboard
oc get route quarkus-demo -n quarkus-demo
```

The rollout runs 10 replicas and moves through 10% → 60% → 100% with two AI analyses. The dashboard shows the traffic split, the decision card, the grader's quality check, and the agent activity feed.

## Run locally

The application runs outside the cluster for UI work. The dashboard calls the agent for rollout control, so set the agent URL when it is not reachable at the in-cluster address:

```bash
export KUBERNETES_AGENT_URL=http://localhost:8080
./mvnw quarkus:dev
```

Then open `http://localhost:8080`. Without a reachable agent the rollout status endpoints return an error, but the UI and the static views still load.

## Build and publish

Build the JAR:

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Build a container image:

```bash
docker build -f src/main/docker/Dockerfile.jvm \
  -t ghcr.io/kdubois/argo-rollouts-quarkus-demo:local .
```

The scenario images are built by GitHub Actions, not by hand. See [SCENARIOS.md](SCENARIOS.md) for the workflow and the tags.

## Change the running version

The active image is a line in `progressive-delivery/workloads/quarkus-rollouts-demo/base/rollout.yaml`. Edit it, commit, and push; Argo CD syncs it and Argo Rollouts starts a canary. Do not use `kubectl set image`, since the next sync reverts it.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `ROLLOUT_NAME` | `quarkus-demo` | Rollout the dashboard reports on |
| `ROLLOUT_NAMESPACE` | `quarkus-demo` | Namespace of the rollout |
| `KUBERNETES_AGENT_URL` | `http://kubernetes-agent.openshift-gitops.svc.cluster.local:8080` | Agent used for rollout control |
| `REMEDIATION_AGENT_URL` | `http://remediation-agent.openshift-gitops.svc.cluster.local:8080` | Remediation service shown in the dashboard |
| `QUARKUS_HTTP_PORT` | `8080` | HTTP port |

The scenario flags (`enable.null.pointer.bug`, `enable.memory.leak`, `enable.latency.drift`) are compiled in by the build workflow. Setting them at runtime does nothing.

## Troubleshooting

**The route returns 503**: check that pods are ready and the stable service has endpoints.

```bash
oc get pods -n quarkus-demo
oc get endpoints quarkus-demo-stable -n quarkus-demo
```

**The dashboard cannot reach the agent**: check the agent service and the URL the app uses.

```bash
oc get svc kubernetes-agent -n openshift-gitops
POD=$(oc get pods -n quarkus-demo -l role=stable -o name | head -1)
oc exec -n quarkus-demo "$POD" -- curl -s http://kubernetes-agent.openshift-gitops.svc.cluster.local:8080/q/health
```

**A rollout is stuck in Paused**: the pause gate may have held it, or a previous demo left it paused.

```bash
oc argo rollouts get rollout quarkus-demo -n quarkus-demo
oc argo rollouts promote quarkus-demo -n quarkus-demo
# or abort
oc argo rollouts abort quarkus-demo -n quarkus-demo
```
