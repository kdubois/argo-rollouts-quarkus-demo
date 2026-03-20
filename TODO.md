# TODO

* PR is inaccurate. -> expected since the LLM doesn't have access to the code. either have it read the code in github or use external dev spaces or something

* issue could be more descriptive

* prompt externalized to a config map?

* The current rollout message isn't very useful. add an AI service to the argo-rollouts-quarkus-demo that uses the same model configs as the kubernetes-agent. The ai service should create a summarization of the kubernetes agent analysis and adds it to the screen. Make this asynchronous though.

* it always says "waiting for analysis to start" on the dashboard, even when an analysis is underway. 

* integrate bob shell to analyze the code from issues and create PRs or let someone review them.