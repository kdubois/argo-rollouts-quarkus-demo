# TODO

## Dashboard Issues

The dashboard currently has a bug where the status indicator does not update when an analysis starts. The UI should reflect the transition from waiting to active analysis state.

When an analysis fails, the dashboard should display a summary of the relevant error logs from the Kubernetes agent. This will help users quickly understand what went wrong without having to check the agent logs separately.

## Metrics and Visualization

The success rate gauge is not accurately representing the deployment state. Consider splitting this into two separate gauges: one showing the success rate of the stable version and another for the canary version. This would provide clearer visibility into how each version is performing.

The analysis process should incorporate actual metrics data in addition to log analysis. Currently it relies too heavily on logs, but metrics would provide more quantitative insights into deployment health.

## User Experience

The dashboard needs a visual refresh to look more modern and polished. The current design feels dated and could benefit from improved styling and layout.

~~The rollout name "argo-rollouts-quarkus-demo" is verbose and cumbersome to reference. Rename it to simply "quarkus-demo" for easier command-line usage and better readability in the UI.~~ ✅ COMPLETED

# Create github actions to build/push images