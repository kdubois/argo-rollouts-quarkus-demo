#!/bin/bash

# Test script for bug scenarios
# This script tests each bug scenario individually to verify they work correctly

set -e

echo "==================================="
echo "Bug Scenarios Test Suite"
echo "==================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to test scenario endpoint
test_scenario_endpoint() {
    echo -e "${YELLOW}Testing /api/scenario endpoint...${NC}"
    response=$(curl -s http://localhost:8080/api/scenario)
    echo "Response: $response"
    echo ""
}

# Function to test status endpoint
test_status_endpoint() {
    echo -e "${YELLOW}Testing /api/status endpoint...${NC}"
    for i in {1..5}; do
        curl -s http://localhost:8080/api/status | jq -r '.status, .successRate'
        sleep 0.5
    done
    echo ""
}

# Function to check metrics
check_metrics() {
    echo -e "${YELLOW}Checking Prometheus metrics...${NC}"
    curl -s http://localhost:8080/q/metrics | grep -E "http_requests_|app_version_info" | head -20
    echo ""
}

echo "==================================="
echo "Test 1: Normal Operation (No Bugs)"
echo "==================================="
echo ""
echo "Starting application without bug flags..."
echo "Please start the app with: ./mvnw quarkus:dev"
echo "Press Enter when ready..."
read

test_scenario_endpoint
test_status_endpoint
check_metrics

echo -e "${GREEN}✓ Normal operation test complete${NC}"
echo ""
echo "Press Enter to continue to Memory Leak test..."
read

echo "==================================="
echo "Test 2: Memory Leak Scenario"
echo "==================================="
echo ""
echo "Please restart the app with: ENABLE_MEMORY_LEAK=true ./mvnw quarkus:dev"
echo "Press Enter when ready..."
read

test_scenario_endpoint

echo -e "${YELLOW}Generating load to trigger memory leak...${NC}"
echo "Making 100 requests..."
for i in {1..100}; do
    curl -s http://localhost:8080/api/status > /dev/null
    if [ $((i % 10)) -eq 0 ]; then
        echo "  Completed $i requests..."
    fi
done
echo ""

echo -e "${YELLOW}Checking for memory leak warnings in logs...${NC}"
echo "Look for: 'MEMORY LEAK DETECTED' messages in the application logs"
echo ""

test_status_endpoint
check_metrics

echo -e "${GREEN}✓ Memory leak test complete${NC}"
echo "Expected: Gradual performance degradation, memory warnings in logs"
echo ""
echo "Press Enter to continue to Connection Leak test..."
read

echo "==================================="
echo "Test 3: Connection Leak Scenario"
echo "==================================="
echo ""
echo "Please restart the app with: ENABLE_CONNECTION_LEAK=true ./mvnw quarkus:dev"
echo "Press Enter when ready..."
read

test_scenario_endpoint

echo -e "${YELLOW}Generating load to exhaust connection pool...${NC}"
echo "Making 200 requests to trigger connection exhaustion..."
for i in {1..200}; do
    curl -s http://localhost:8080/api/status > /dev/null 2>&1 || true
    if [ $((i % 20)) -eq 0 ]; then
        echo "  Completed $i requests..."
    fi
done
echo ""

echo -e "${YELLOW}Checking for connection pool errors in logs...${NC}"
echo "Look for: 'CONNECTION POOL EXHAUSTED' messages in the application logs"
echo ""

test_status_endpoint
check_metrics

echo -e "${GREEN}✓ Connection leak test complete${NC}"
echo "Expected: Success rate drops to near 0%, connection pool exhaustion errors"
echo ""
echo "Press Enter to continue to CPU Spike test..."
read

echo "==================================="
echo "Test 4: CPU Spike Scenario"
echo "==================================="
echo ""
echo "Please restart the app with: ENABLE_CPU_SPIKE=true ./mvnw quarkus:dev"
echo "Press Enter when ready..."
read

test_scenario_endpoint

echo -e "${YELLOW}Generating load to trigger CPU spikes...${NC}"
echo "Making 50 requests (every 10th will spike)..."
for i in {1..50}; do
    start=$(date +%s%N)
    curl -s http://localhost:8080/api/status > /dev/null
    end=$(date +%s%N)
    duration=$(( (end - start) / 1000000 ))
    
    if [ $((i % 10)) -eq 0 ]; then
        echo "  Request $i: ${duration}ms (should be slow due to CPU spike)"
    fi
done
echo ""

echo -e "${YELLOW}Checking for CPU spike warnings in logs...${NC}"
echo "Look for: 'CPU SPIKE TRIGGERED' messages in the application logs"
echo ""

test_status_endpoint
check_metrics

echo -e "${GREEN}✓ CPU spike test complete${NC}"
echo "Expected: Periodic latency spikes every 10th request, CPU warnings in logs"
echo ""

echo "==================================="
echo "Test 5: Multiple Bugs Combined"
echo "==================================="
echo ""
echo "Please restart the app with all flags:"
echo "ENABLE_MEMORY_LEAK=true ENABLE_CONNECTION_LEAK=true ENABLE_CPU_SPIKE=true ./mvnw quarkus:dev"
echo "Press Enter when ready..."
read

test_scenario_endpoint

echo -e "${YELLOW}Generating load with all bugs active...${NC}"
echo "Making 100 requests..."
for i in {1..100}; do
    curl -s http://localhost:8080/api/status > /dev/null 2>&1 || true
    if [ $((i % 10)) -eq 0 ]; then
        echo "  Completed $i requests..."
    fi
done
echo ""

test_status_endpoint
check_metrics

echo -e "${GREEN}✓ Combined bugs test complete${NC}"
echo "Expected: Multiple types of errors and degradation"
echo ""

echo "==================================="
echo "All Tests Complete!"
echo "==================================="
echo ""
echo "Summary:"
echo "1. ✓ Normal operation - baseline metrics"
echo "2. ✓ Memory leak - gradual degradation"
echo "3. ✓ Connection leak - sharp failure"
echo "4. ✓ CPU spike - periodic latency spikes"
echo "5. ✓ Combined bugs - multiple issues"
echo ""
echo "Review the application logs for detailed error messages and warnings."
echo "These logs are what a code assistant would analyze to propose fixes."

