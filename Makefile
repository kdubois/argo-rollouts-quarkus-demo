.PHONY: help build docker-build docker-push deploy undeploy dev test clean

# Variables
IMAGE_NAME := quay.io/kdubois/demo-app
IMAGE_TAG ?= latest
FULL_IMAGE := $(IMAGE_NAME):$(IMAGE_TAG)

# Default target
.DEFAULT_GOAL := help

help: ## Display available targets
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

build: ## Build the Quarkus application
	@echo "Building Quarkus application..."
	./mvnw clean package -DskipTests
	@echo "Build complete: target/quarkus-app/quarkus-run.jar"

docker-build: build ## Build Docker image
	@echo "Building Docker image: $(FULL_IMAGE)"
	docker build -f src/main/docker/Dockerfile.jvm -t $(FULL_IMAGE) .
	@echo "Docker image built successfully: $(FULL_IMAGE)"

docker-push: docker-build ## Push Docker image to registry
	@echo "Pushing Docker image: $(FULL_IMAGE)"
	docker push $(FULL_IMAGE)
	@echo "Docker image pushed successfully"

deploy: ## Deploy to Kubernetes using kustomize
	@echo "Deploying to Kubernetes..."
	kubectl apply -k kubernetes/
	@echo "Waiting for rollout to be ready..."
	kubectl wait --for=condition=available --timeout=300s rollout/demo-app -n demo-app || true
	kubectl argo rollouts status demo-app -n demo-app
	@echo "Deployment complete"

undeploy: ## Remove from Kubernetes
	@echo "Removing from Kubernetes..."
	kubectl delete -k kubernetes/ --ignore-not-found=true
	@echo "Undeployment complete"

dev: ## Run in Quarkus dev mode
	@echo "Starting Quarkus dev mode..."
	./mvnw quarkus:dev

test: ## Run tests
	@echo "Running tests..."
	./mvnw test
	@echo "Tests complete"

clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	./mvnw clean
	rm -rf target/
	@echo "Clean complete"

# Advanced targets

docker-build-native: ## Build native Docker image (requires GraalVM)
	@echo "Building native Docker image: $(FULL_IMAGE)-native"
	./mvnw package -Dnative -Dquarkus.native.container-build=true
	docker build -f src/main/docker/Dockerfile.native -t $(FULL_IMAGE)-native .
	@echo "Native Docker image built successfully"

docker-push-native: docker-build-native ## Push native Docker image to registry
	@echo "Pushing native Docker image: $(FULL_IMAGE)-native"
	docker push $(FULL_IMAGE)-native
	@echo "Native Docker image pushed successfully"

verify: ## Verify deployment
	@echo "Verifying deployment..."
	@echo "\nRollout status:"
	kubectl get rollout demo-app -n demo-app
	@echo "\nPods:"
	kubectl get pods -n demo-app
	@echo "\nServices:"
	kubectl get svc -n demo-app
	@echo "\nIstio resources:"
	kubectl get gateway,virtualservice -n demo-app

logs: ## Show application logs
	@echo "Showing application logs..."
	kubectl logs -n demo-app -l app=demo-app --tail=100 -f

rollout-status: ## Watch rollout status
	kubectl argo rollouts get rollout demo-app -n demo-app --watch

promote: ## Manually promote rollout
	@echo "Promoting rollout..."
	kubectl argo rollouts promote demo-app -n demo-app
	@echo "Rollout promoted"

abort: ## Abort current rollout
	@echo "Aborting rollout..."
	kubectl argo rollouts abort demo-app -n demo-app
	@echo "Rollout aborted"

restart: ## Restart rollout
	@echo "Restarting rollout..."
	kubectl argo rollouts restart demo-app -n demo-app
	@echo "Rollout restarted"

# Development helpers

port-forward: ## Port forward to application
	@echo "Port forwarding to demo-app-stable:8080..."
	kubectl port-forward -n demo-app svc/demo-app-stable 8080:8080

format: ## Format code
	@echo "Formatting code..."
	./mvnw fmt:format
	@echo "Code formatted"

# CI/CD helpers

ci-build: ## Build for CI (with tests)
	@echo "Building for CI..."
	./mvnw clean verify
	@echo "CI build complete"

ci-docker: ## Build and push Docker image for CI
	@echo "Building and pushing Docker image for CI..."
	@echo "Image: $(FULL_IMAGE)"
	./mvnw clean package -DskipTests
	docker build -f src/main/docker/Dockerfile.jvm -t $(FULL_IMAGE) .
	docker push $(FULL_IMAGE)
	@echo "CI Docker build and push complete"

# Demo helpers

demo-happy: ## Deploy happy scenario
	@echo "Deploying happy scenario..."
	kubectl patch rollout demo-app -n demo-app --type merge -p '{"spec":{"template":{"spec":{"containers":[{"name":"demo-app","env":[{"name":"SCENARIO_MODE","value":"happy"},{"name":"APP_VERSION","value":"1.0.0"}]}]}}}}'
	@echo "Happy scenario deployed"

demo-failure: ## Deploy failure scenario
	@echo "Deploying failure scenario..."
	kubectl patch rollout demo-app -n demo-app --type merge -p '{"spec":{"template":{"spec":{"containers":[{"name":"demo-app","env":[{"name":"SCENARIO_MODE","value":"failure"},{"name":"APP_VERSION","value":"2.0.0"}]}]}}}}'
	@echo "Failure scenario deployed"

demo-reset: ## Reset demo to initial state
	@echo "Resetting demo..."
	kubectl argo rollouts abort demo-app -n demo-app || true
	kubectl argo rollouts promote demo-app -n demo-app --full || true
	kubectl patch rollout demo-app -n demo-app --type merge -p '{"spec":{"template":{"spec":{"containers":[{"name":"demo-app","env":[{"name":"SCENARIO_MODE","value":"happy"},{"name":"APP_VERSION","value":"1.0.0"}]}]}}}}'
	@echo "Demo reset complete"