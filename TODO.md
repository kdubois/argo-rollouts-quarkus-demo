# TODO


* The current rollout message isn't very useful. add an AI service to the argo-rollouts-quarkus-demo that summarizes the kubernetes agent analysis and adds it to the screen. Make this asynchronous though.

* The analysis sometimes takes too long, resulting in the rollout going to > 60% before being rolled back.  There is a lot of back-and-forth between the agents and the LLM. can we reduce this?

* Remove the old KubernetesAgent

* it always says "waiting for analysis to start" on the dashboard, even when an analysis is underway. 