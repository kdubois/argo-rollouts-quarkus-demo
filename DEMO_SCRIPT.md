# Conference demo script

A run-through of the four scenarios in about eight minutes, with talking points and the commands that show each step.

## Before the demo

Check the stack is healthy:

```bash
# Argo CD, Argo Rollouts, and the plugin
oc get pods -n openshift-gitops | grep -E "argo-rollouts|kubernetes-agent"

# Demo namespace and rollout
oc get rollout quarkus-demo -n quarkus-demo
oc get pods -n quarkus-demo

# Dashboard
oc get route quarkus-demo -n quarkus-demo
```

Open the dashboard in a browser and keep it visible. It shows the rollout, the live traffic split, the decision card with the quality check, and the agent activity feed.

Set up two terminals:

```bash
# Terminal 1 — rollout status
oc argo rollouts get rollout quarkus-demo -n quarkus-demo --watch

# Terminal 2 — agent logs
oc logs -f deployment/kubernetes-agent -n openshift-gitops
```

Trigger each scenario from the `progressive-delivery` repository. Edit the image line in `workloads/quarkus-rollouts-demo/base/rollout.yaml`, commit, and push. Argo CD syncs the change in a few seconds; do not run `kubectl set image`, because the sync reverts it.

## Opening

"How many of you have had a bad deployment reach production? Canary deployments give you a smaller blast radius, but someone still has to watch the graphs and decide whether to continue. In this demo an AI agent does that: it reads the canary's logs and metrics, decides promote or roll back, and when it rolls back it opens a fix."

The stack is Argo CD for GitOps, Argo Rollouts for the canary, a Go metric plugin, and a Quarkus agent that owns the LLM and the Kubernetes tools. The plugin sends selectors to the agent and returns the agent's decision to Argo Rollouts. There is no model inside the plugin.

## Scenario 1: a healthy rollout

**Duration**: about 2 minutes, after CI has built the images.

Every push to the demo repository makes CI build a fresh stable image and open a `Deploy demo build <sha>` PR in
`progressive-delivery`. Merge that PR (or change the image line by hand) and push; Argo CD syncs the manifest in a few
seconds and the canary starts.

```bash
# In progressive-delivery
gh pr list --search "Deploy demo build" --state open
gh pr merge <number> --squash
```

Talking points while the rollout advances:

- The canary takes 10% of traffic first. The app generates its own load, 300 requests per second per pod, so there is data immediately.
- The first analysis runs during the initial pause. A second one runs at 60% weight. Both have to pass before the rollout reaches 100%.
- The agent fetches logs and metrics for stable and canary, compares them, and returns a decision with a confidence score.

What to show:

```bash
oc argo rollouts get rollout quarkus-demo -n quarkus-demo
oc get analysisrun -n quarkus-demo
oc describe analysisrun -n quarkus-demo <name>
```

On the dashboard, point at the decision card: the model's confidence, the grader's quality check, and the activity feed. Both measurements should show a passed quality check.

## Scenario 2: a code bug, a rollback, and a pull request

**Duration**: about 2 minutes.

Set the image to `v2.nullpointer` and push.

```bash
# In progressive-delivery: image: ...:v2.nullpointer
git add . && git commit -m "demo: null pointer bug" && git push
```

The canary throws `NullPointerException` in `UserResource` on 20% of user lookups. Stable does not.

```bash
oc logs -n quarkus-demo -l role=canary --tail=30
oc argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

Talking points:

- The agent sees the exception on the canary only. A repeated canary-only exception is evidence even at a low rate.
- It rolls back instead of promoting, then delegates to the remediation agent over A2A.
- The remediation agent runs in a sandbox, reproduces the failure, patches the null check, runs the test suite, and opens a pull request. The PR carries the verification output.

Show the AnalysisRun message and the pull request in the browser. Close the PR after the demo; the next run needs the bug still present.

## Scenario 3: an operational problem, an issue instead of a patch

**Duration**: about 2 minutes.

Set the image to `v3.memoryleak` and push. The canary allocates 1MB per request and never releases it.

```bash
oc logs -n quarkus-demo -l role=canary --tail=10 -f
```

Talking points:

- The logs are vague: response times increasing, heap at 50MB, then 100MB. No stack trace.
- Latency climbs as the heap fills, so the metrics carry the signal.
- The agent rolls back. Because this is not a patchable code defect, the remediation agent opens an issue with the evidence and the investigation steps. It does not invent a patch.

This is the case that shows the split: the decision is always promote or roll back, but the remediation path depends on the kind of failure.

## Scenario 4: a metrics-only regression

**Duration**: about 2 minutes.

Set the image to `v4.latency` and push.

Talking points:

- The canary has no errors. Success rate stays at 99%, the logs stay clean.
- For the first 20 seconds of pod uptime it adds no delay, so the first analysis sees baseline latency and passes.
- After that, latency grows 5ms per second, capped at 250ms. By the second measurement the canary p95 is well above stable, and the rollout aborts on metrics alone.

```bash
oc get analysisrun -n quarkus-demo -w
oc argo rollouts get rollout quarkus-demo -n quarkus-demo
```

The point: one analysis early in a rollout can be fooled. Recurring analysis is what catches a slow burn.

## The pause gate

The AnalysisTemplate sets `pauseOnUncertainty: true` with a threshold of 50. When the analysis promotes but the evidence is weak, the agent patches the rollout to paused instead of letting it through. The dashboard shows a pulsing traffic bar with Promote and Abort buttons; both call the agent, which drives the Argo Rollouts API.

If the grader contradicts a promote decision, the rollout is paused and the quality check is marked failed. The analysis then escalates to the second opinion model and re-runs, and the activity feed names both models and both decisions.

The gate is a demo of the human-in-the-loop path: the measurement stays successful, the pause holds the rollout, and a person decides.

## Q&A talking points

**Does the plugin call the model?** No. The plugin is a thin Go provider that sends pod selectors and context to the agent over HTTP. The agent owns the LLM, the Kubernetes tools, and the prompt.

**Which models work?** Any OpenAI-compatible endpoint: OpenAI, Gemini's compatible endpoint, vLLM, LiteLLM, or an internal gateway. The deployment switches models through a ConfigMap and a restart, no code change.

**How does it avoid false rollbacks on a small sample?** The metrics are collected over a window and the window extends until the canary has at least 500 requests. The grader also judges the canary against that floor rather than against stable's lifetime total.

**What stops a hallucinated analysis?** The decision-model grader asks whether the evidence contradicts the conclusion and whether there is enough data to verify it. A contradicted conclusion escalates to the second opinion model; a promote that survives the comparison with weak evidence pauses for a human.

**Does it fix production?** It opens a pull request or an issue. The agent does not merge, and the sandbox is read-only outside its workspace.

**What did it cost to run?** The analysis and escalation tiers are small open-weight models behind an OpenAI-compatible gateway. The heavy lifting is the tool calls around the model, not the token count.

## Reset after the demo

Set the rollout back to stable and push:

```bash
# In progressive-delivery
# image: ghcr.io/kdubois/argo-rollouts-quarkus-demo:v1.stable
git add . && git commit -m "demo: back to stable" && git push
oc argo rollouts get rollout quarkus-demo -n quarkus-demo --watch
```

Close any pull requests and issues the remediation agent opened. They are real artifacts against the demo repository and will fail the next run if left open.

```bash
gh pr list --repo kdubois/argo-rollouts-aiops-dashboard
gh issue list --repo kdubois/argo-rollouts-aiops-dashboard
```

## If something goes wrong

**The rollout did not start**: confirm the image line changed and Argo CD synced.

```bash
oc get rollout quarkus-demo -n quarkus-demo -o jsonpath='{.spec.template.spec.containers[0].image}'
oc get application quarkus-rollouts-demo -n openshift-gitops -o jsonpath='{.status.sync.status}'
```

**No AnalysisRun**: check the plugin registered and the agent is reachable.

```bash
oc logs deployment/argo-rollouts -n openshift-gitops | grep -i metric-ai
oc logs deployment/kubernetes-agent -n openshift-gitops --tail=50
```

If the plugin is missing on a fresh cluster, delete the Argo Rollouts pod so the operator re-registers it.

**The agent rolls back a healthy canary**: read the quality check in the activity feed. A failed grade with a canary-only exception is the intended path; a failed grade on a thin sample is a bug worth screenshotting.

**The latency scenario passes twice**: the drift starts 20 seconds after pod start. If the canary was already warm, the second measurement may still be inside the grace period. Retry the rollout rather than editing the manifest mid-demo.
