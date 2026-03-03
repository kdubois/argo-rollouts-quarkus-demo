# GitHub Issues to Create

Copy and paste each section below into GitHub's issue creation form at:
https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new

---

## Issue 1: Dashboard does not update when analysis starts

**Title:** Dashboard does not update when analysis starts

**Body:**
The dashboard currently has a bug where the status indicator does not update when an analysis starts. The UI should reflect the transition from waiting to active analysis state.

---

## Issue 2: Display analysis failure summary in dashboard

**Title:** Display analysis failure summary in dashboard

**Body:**
When an analysis fails, the dashboard should display a summary of the relevant error logs from the Kubernetes agent. This will help users quickly understand what went wrong without having to check the agent logs separately.

---

## Issue 3: Split success rate gauge into stable and canary metrics

**Title:** Split success rate gauge into stable and canary metrics

**Body:**
The success rate gauge is not accurately representing the deployment state. Consider splitting this into two separate gauges: one showing the success rate of the stable version and another for the canary version. This would provide clearer visibility into how each version is performing.

---

## Issue 4: Incorporate metrics data into analysis process

**Title:** Incorporate metrics data into analysis process

**Body:**
The analysis process should incorporate actual metrics data in addition to log analysis. Currently it relies too heavily on logs, but metrics would provide more quantitative insights into deployment health.

---

## Issue 5: Modernize dashboard design

**Title:** Modernize dashboard design

**Body:**
The dashboard needs a visual refresh to look more modern and polished. The current design feels dated and could benefit from improved styling and layout.

---

## ~~Issue 6: Rename rollout from "argo-rollouts-quarkus-demo" to "quarkus-demo"~~ ✅ COMPLETED

**Title:** ~~Rename rollout from "argo-rollouts-quarkus-demo" to "quarkus-demo"~~

**Body:**
~~The rollout name "argo-rollouts-quarkus-demo" is verbose and cumbersome to reference. Rename it to simply "quarkus-demo" for easier command-line usage and better readability in the UI.~~

---

## Quick Create Links

You can also use these direct links (you'll need to be logged into GitHub):

1. [Create Issue 1](https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new?title=Dashboard%20does%20not%20update%20when%20analysis%20starts&body=The%20dashboard%20currently%20has%20a%20bug%20where%20the%20status%20indicator%20does%20not%20update%20when%20an%20analysis%20starts.%20The%20UI%20should%20reflect%20the%20transition%20from%20waiting%20to%20active%20analysis%20state.)
2. [Create Issue 2](https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new?title=Display%20analysis%20failure%20summary%20in%20dashboard&body=When%20an%20analysis%20fails%2C%20the%20dashboard%20should%20display%20a%20summary%20of%20the%20relevant%20error%20logs%20from%20the%20Kubernetes%20agent.%20This%20will%20help%20users%20quickly%20understand%20what%20went%20wrong%20without%20having%20to%20check%20the%20agent%20logs%20separately.)
3. [Create Issue 3](https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new?title=Split%20success%20rate%20gauge%20into%20stable%20and%20canary%20metrics&body=The%20success%20rate%20gauge%20is%20not%20accurately%20representing%20the%20deployment%20state.%20Consider%20splitting%20this%20into%20two%20separate%20gauges%3A%20one%20showing%20the%20success%20rate%20of%20the%20stable%20version%20and%20another%20for%20the%20canary%20version.%20This%20would%20provide%20clearer%20visibility%20into%20how%20each%20version%20is%20performing.)
4. [Create Issue 4](https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new?title=Incorporate%20metrics%20data%20into%20analysis%20process&body=The%20analysis%20process%20should%20incorporate%20actual%20metrics%20data%20in%20addition%20to%20log%20analysis.%20Currently%20it%20relies%20too%20heavily%20on%20logs%2C%20but%20metrics%20would%20provide%20more%20quantitative%20insights%20into%20deployment%20health.)
5. [Create Issue 5](https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new?title=Modernize%20dashboard%20design&body=The%20dashboard%20needs%20a%20visual%20refresh%20to%20look%20more%20modern%20and%20polished.%20The%20current%20design%20feels%20dated%20and%20could%20benefit%20from%20improved%20styling%20and%20layout.)
6. [Create Issue 6](https://github.com/kdubois/argo-rollouts-quarkus-demo/issues/new?title=Rename%20rollout%20from%20%22argo-rollouts-quarkus-demo%22%20to%20%22quarkus-demo%22&body=The%20rollout%20name%20%22argo-rollouts-quarkus-demo%22%20is%20verbose%20and%20cumbersome%20to%20reference.%20Rename%20it%20to%20simply%20%22quarkus-demo%22%20for%20easier%20command-line%20usage%20and%20better%20readability%20in%20the%20UI.)