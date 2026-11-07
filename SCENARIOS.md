# Demo scenarios

Four container images, each with a different failure mode baked in at build time. The rollout, AnalysisTemplate, and services live in the [progressive-delivery](../progressive-delivery) repository; this document explains what each scenario does and what the agent is expected to decide.

## The four images

| Tag | Behaviour | What the agent sees | Expected outcome |
|---|---|---|---|
| `v1.stable` | 99% success, 10–50ms latency | No errors in canary or stable | Promote |
| `v2.nullpointer` | `NullPointerException` in `UserResource` for 20% of user lookups | Canary-only stack traces, error rate above stable | Roll back, open a PR with a verified fix |
| `v3.memoryleak` | Allocates 1MB per request, never releases; latency grows to 300ms and heap climbs | Heap and latency regression, vague warning logs, no stack trace | Roll back, open an issue |
| `v4.latency` | No added delay for the first 20s of pod uptime, then +5ms per second, capped at 250ms | Clean logs and 99% success, p95 latency above stable only after the grace period | Roll back once the metrics show the drift |

Bug scenarios are compiled into the JAR. The feature flags in `src/main/resources/application.properties` are set by the build workflow before `mvn package`, so changing an environment variable at runtime has no effect.

## Rollout shape

The demo runs 10 replicas with an OpenShift Route traffic split:

| Step | Weight | Pause | Analysis |
|---|---|---|---|
| 1 | 10% | 10s | Measurement 1 starts after the 5s initial delay |
| 3 | 10% | 30s | Measurement 1 completes here |
| 5 | 60% | 90s | Measurement 2 runs and completes here |
| 7 | 100% | 5s | Full promotion |

The AnalysisTemplate runs `count: 2` with `interval: 30s`, so the rollout needs two successful analyses before it reaches 100%. A rollback in either measurement aborts the rollout and restores the stable version.

Each pod generates 300 requests per second against itself: 80% to `/api/status` and 20% to `/api/user`, which is where the NullPointerException lives. The first measurement therefore has enough traffic to judge the canary, and the second can see a slow regression that the first one missed.

After a rollback the agent delegates to the remediation agent (A2A). A patchable code defect produces a pull request with test evidence; an operational problem such as a memory leak produces an issue with the investigation steps. Both paths are asynchronous, so the promote/rollback response is not delayed.

## Switching scenarios

The active image is a line in [`workloads/quarkus-rollouts-demo/base/rollout.yaml`](../progressive-delivery/workloads/quarkus-rollouts-demo/base/rollout.yaml). Change it, commit, and push; Argo CD syncs it and Argo Rollouts starts the canary.

```bash
cd ../progressive-delivery
vim workloads/quarkus-rollouts-demo/base/rollout.yaml
git add . && git commit -m "demo: run the null pointer scenario" && git push
```

The base manifest ships with all four image lines commented except `v1.stable`, so switching is a two-line edit. There are kustomize overlays under `workloads/quarkus-rollouts-demo/overlays/` for the same purpose, but the base manifest is what the ApplicationSet deploys in this demo.

Do not run `kubectl set image`: Argo CD reconciles the manifest back and the demo drifts.

## Demo flow

The full run is about eight minutes. Open the demo dashboard (`oc get route quarkus-demo -n quarkus-demo`) in a second window; it shows the rollout, the live traffic split, and the agent activity feed.

### 1. Stable rollout

Set the image to `v1.stable` and push. The canary reaches 10%, both analyses pass, and the rollout promotes to 100%. The dashboard shows two passed quality checks.

```bash
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
kubectl get analysisrun -n quarkus-demo
```

### 2. Code bug with rollback and PR

Set the image to `v2.nullpointer` and push. The canary logs carry `NullPointerException` while stable stays clean. The first analysis rolls back. The remediation agent then reproduces the bug, patches `UserResource`, runs the tests, and opens a pull request.

```bash
kubectl logs -n quarkus-demo -l role=canary --tail=30
kubectl argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

### 3. Memory leak with rollback and issue

Set the image to `v3.memoryleak` and push. Heap and latency climb; the logs only say response times are increasing, with no stack trace. The analysis rolls back and the remediation agent opens an issue instead of a speculative patch.

```bash
kubectl logs -n quarkus-demo -l role=canary --tail=10 -f
```

### 4. Metrics-only regression

Set the image to `v4.latency` and push. The first analysis passes because the 20-second grace period has not elapsed. By the second measurement the canary p95 is above the stable baseline while the success rate is still 99% and the logs are clean, so the rollout aborts on metrics alone. This is the case that shows why analysis has to repeat through the rollout.

## Reading the agent output

The dashboard and the `AnalysisRun` status show the decision and the quality check. `kubectl describe analysisrun` prints the metric message, and the agent activity feed shows the order:

1. `CONFIDENCE_SCORE`: the analysis model's self-reported confidence.
2. `QUALITY_CHECK`: the Jev grader's verdict and score. `passed` (≥ 50) means the evidence backs the conclusion; `neutral` (30–50) means the evidence is too thin; `failed` (< 30) means it contradicts the conclusion.
3. `DECISION`: the effective confidence, with both inputs. A failed quality check escalates to the strong model; a contradicted promote decision pauses the rollout when the pause gate is on.

## Building the images

GitHub Actions builds all four images on every push to `main` using [`.github/workflows/build-scenario-images.yml`](.github/workflows/build-scenario-images.yml). The workflow sets the feature flags, builds with Maven, and pushes to GitHub Container Registry:

- `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable`
- `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v2.nullpointer`
- `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v3.memoryleak`
- `ghcr.io/kdubois/argo-rollouts-quarkus-demo:v4.latency`

To build one locally, set the flags, build the JAR, and build the image:

```bash
sed -i 's/enable.memory.leak=.*/enable.memory.leak=true/' src/main/resources/application.properties
./mvnw clean package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm \
  -t ghcr.io/kdubois/argo-rollouts-quarkus-demo:test .
```

Reset `application.properties` afterwards; the workflow overwrites it per matrix entry anyway, but a local build should not leave a bug enabled.

## Troubleshooting

### The rollout does not start after a push

Check that the image line actually changed and that Argo CD synced:

```bash
oc get rollout quarkus-demo -n quarkus-demo -o jsonpath='{.spec.template.spec.containers[0].image}'
oc get application quarkus-rollouts-demo -n openshift-gitops -o jsonpath='{.status.sync.status}'
```

### No `AnalysisRun` appears

Check the AnalysisTemplate reference and the plugin:

```bash
oc get analysisrun -n quarkus-demo
oc logs deployment/argo-rollouts -n openshift-gitops | grep -i "metric-ai"
oc logs deployment/kubernetes-agent -n openshift-gitops --tail=50
```

The plugin must be registered in the `argo-rollouts-config` ConfigMap. If it is missing on a fresh install, delete the Argo Rollouts pod so the operator re-registers it.

### The latency scenario passes every analysis

The drift starts after 20 seconds of pod uptime. If the canary pods were already running, the effect is visible sooner; if the second measurement still passes, check that the rollout has reached the 60% step and that the metrics window is collecting:

```bash
oc get analysisrun -n quarkus-demo -o yaml | grep -A5 "message"
```

### Generated PRs and issues pile up

Each demo run with `githubUrl` set opens a real artifact when it rolls back. Close them after the run so the next run starts clean. To skip remediation entirely, remove `githubUrl` from the AnalysisTemplate in `progressive-delivery`.
