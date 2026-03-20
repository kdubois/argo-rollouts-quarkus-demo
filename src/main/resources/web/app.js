// Dashboard JavaScript - Extracted from dashboard.html
// This app will be bundled by the Web-Bundler and available using the {#bundle /} tag

let previousSuccessRate = null;
let previousErrorRate = null;

function updateDashboard() {
    Promise.all([
        fetch('/api/status').then(r => r.json()),
        fetch('/api/rollout/status').then(r => r.json()),
        fetch('/api/rollout/scenarios').then(r => r.json()),
        fetch('/api/rollout/metrics').then(r => r.json())
    ])
    .then(([metricsData, rolloutData, scenariosData, versionMetrics]) => {
        updateRolloutProgress(rolloutData);
        updateTrafficDistribution(rolloutData);
        updateAIAnalysis(rolloutData, metricsData, versionMetrics);
        updateScenarios(scenariosData);
        visualizeRealRequests(versionMetrics);
        clearError();
    })
    .catch(error => {
        console.error('Error updating dashboard:', error);
        const isConnectionError = error.message.includes('Failed to fetch') ||
                                 error.message.includes('NetworkError') ||
                                 error.message.includes('fetch');
        if (isConnectionError) {
            showError('⚠️ Not connected to cluster. Running in demo mode with simulated data.');
            showDemoMode();
        } else {
            showError('Failed to fetch dashboard data: ' + error.message);
        }
    });
}

function showDemoMode() {
    document.getElementById('rolloutStatusBadge').innerHTML =
        '<div class="status-badge progressing"><div class="status-dot"></div>Demo Mode</div>';
    
    const deploymentStatusValue = document.getElementById('deploymentStatusValue');
    if (deploymentStatusValue) {
        deploymentStatusValue.textContent = 'Demo Mode';
    }
    
    const rolloutMessage = document.getElementById('rolloutMessage');
    if (rolloutMessage) {
        rolloutMessage.textContent = 'Connect to a Kubernetes cluster to see live data';
    }
}

function updateRolloutProgress(rolloutData) {
    const phase = rolloutData.phase || 'Unknown';
    const canaryWeight = rolloutData.canaryWeight || 0;
    const currentStepIndex = rolloutData.currentStepIndex;
    
    const badge = document.getElementById('rolloutStatusBadge');
    const statusClass = phase.toLowerCase().replace(/\s+/g, '-');
    badge.innerHTML = '<div class="status-badge ' + statusClass + '">' +
        '<div class="status-dot"></div>' +
        phase +
        '</div>';

    const deploymentStatusValue = document.getElementById('deploymentStatusValue');
    if (deploymentStatusValue) {
        deploymentStatusValue.textContent = phase;
    }
    
    const rolloutMessage = document.getElementById('rolloutMessage');
    if (rolloutMessage) {
        let message = rolloutData.message || '';
        if (!message) {
            if (phase === 'Healthy' && canaryWeight === 100) {
                message = 'Rollout completed successfully';
            } else if (phase === 'Progressing') {
                message = 'Rollout in progress';
            } else if (phase === 'Paused') {
                message = 'Rollout paused for analysis';
            } else if (phase === 'Degraded') {
                message = 'Rollout degraded - issues detected';
            } else {
                message = 'Monitoring deployment';
            }
        }
        rolloutMessage.textContent = message;
    }

    const progress = document.getElementById('timelineProgress');
    progress.style.width = canaryWeight + '%';

    const stages = document.querySelectorAll('.stage');
    stages.forEach(stage => {
        const weight = parseInt(stage.dataset.weight);
        const stepAttr = stage.dataset.step;
        stage.classList.remove('active', 'completed', 'paused');
        
        // Check if this stage matches the current step
        let isCurrentStage = false;
        let isPastStage = false;
        if (currentStepIndex !== null && currentStepIndex !== undefined && stepAttr) {
            const steps = stepAttr.split(',').map(s => parseInt(s.trim()));
            isCurrentStage = steps.includes(currentStepIndex);
            // Check if all steps for this stage are less than current step
            isPastStage = steps.every(s => s < currentStepIndex);
        }
        
        // Mark stages as completed if they're in the past
        if (isPastStage || weight < canaryWeight) {
            stage.classList.add('completed');
        } else if (isCurrentStage || (weight === canaryWeight && (currentStepIndex === null || currentStepIndex === undefined))) {
            // Current stage
            if (phase === 'Paused') {
                stage.classList.add('paused');
            } else {
                stage.classList.add('active');
            }
        }
    });
}

function updateTrafficDistribution(rolloutData) {
    const stableWeight = rolloutData.stableWeight || 100;
    const canaryWeight = rolloutData.canaryWeight || 0;
    
    const stableSegment = document.getElementById('stableSegment');
    const canarySegment = document.getElementById('canarySegment');
    const stablePercentage = document.getElementById('stablePercentage');
    const canaryPercentage = document.getElementById('canaryPercentage');
    
    stableSegment.style.width = stableWeight + '%';
    canarySegment.style.width = canaryWeight + '%';
    stablePercentage.textContent = stableWeight + '%';
    canaryPercentage.textContent = canaryWeight + '%';
    
    stablePercentage.style.display = stableWeight < 15 ? 'none' : 'flex';
    canaryPercentage.style.display = canaryWeight < 15 ? 'none' : 'flex';
}

function updateAIAnalysis(rolloutData, metricsData, versionMetrics) {
    try {
        const aiIcon = document.getElementById('aiIcon');
        const aiStatusTitle = document.getElementById('aiStatusTitle');
        const aiStatusSubtitle = document.getElementById('aiStatusSubtitle');
        const aiDecision = document.getElementById('aiDecision');
        const aiDecisionTitle = document.getElementById('aiDecisionTitle');
        const aiDecisionMessage = document.getElementById('aiDecisionMessage');
        const errorLogContainer = document.getElementById('errorLogContainer');
        
        if (!aiIcon || !aiStatusTitle || !aiStatusSubtitle || !aiDecision || !aiDecisionTitle || !aiDecisionMessage || !errorLogContainer) {
            console.warn('Some AI panel elements not found, skipping update');
            return;
        }
        
        const stableSuccessRate = Math.round(versionMetrics.stableSuccessRate);
        const canarySuccessRate = Math.round(versionMetrics.canarySuccessRate);
        const totalRequests = versionMetrics.stableRequestCount + versionMetrics.canaryRequestCount;
        
        const stableElement = document.getElementById('stableSuccessRate');
        const canaryElement = document.getElementById('canarySuccessRate');
        const requestsElement = document.getElementById('aiRequests');
        
        if (stableElement) stableElement.textContent = stableSuccessRate + '%';
        if (canaryElement) canaryElement.textContent = canarySuccessRate + '%';
        if (requestsElement) requestsElement.textContent = totalRequests;
    
    const analysis = rolloutData.analysis;

    if (analysis && analysis.phase && analysis.phase !== 'Pending' && analysis.phase !== 'NotStarted') {
        aiIcon.classList.remove('analyzing');
        aiDecision.classList.remove('success', 'failed');

        if (analysis.phase === 'Running' || analysis.phase === 'Progressing' || analysis.phase === 'InProgress') {
            aiIcon.classList.add('analyzing');
            aiIcon.textContent = '🔄';
            aiStatusTitle.textContent = 'Analysis Running';
            aiStatusSubtitle.textContent = 'AI is evaluating deployment metrics';
            aiDecisionTitle.textContent = 'Analyzing...';
            aiDecisionMessage.textContent = analysis.message || 'The AI agent is currently analyzing canary metrics to determine if the rollout should continue.';
            errorLogContainer.style.display = 'none';
        } else if (analysis.phase === 'Successful' || analysis.successful === true) {
            aiIcon.textContent = '✅';
            aiStatusTitle.textContent = 'Analysis Successful';
            aiStatusSubtitle.textContent = 'Metrics within acceptable thresholds';
            aiDecision.classList.add('success');
            aiDecisionTitle.textContent = '✓ Rollout Approved';
            aiDecisionMessage.textContent = analysis.message || 'AI analysis completed successfully. All metrics are healthy. The rollout can proceed to the next stage.';
            errorLogContainer.style.display = 'none';
            
            const canarySegment = document.getElementById('canarySegment');
            canarySegment.classList.remove('degraded');
        } else if (analysis.phase === 'Failed' || analysis.phase === 'Degraded' || analysis.successful === false) {
            aiIcon.textContent = '❌';
            aiStatusTitle.textContent = 'Analysis Failed';
            aiStatusSubtitle.textContent = 'Issues detected in deployment';
            aiDecision.classList.add('failed');
            aiDecisionTitle.textContent = '✗ Rollback Recommended';
            aiDecisionMessage.textContent = analysis.message || 'AI analysis detected issues with the canary deployment. Metrics are outside acceptable thresholds. Rollback is recommended.';
            
            if (analysis.errorLog) {
                errorLogContainer.style.display = 'block';
                document.getElementById('errorLogText').textContent = analysis.errorLog;
            } else {
                errorLogContainer.style.display = 'none';
            }
            
            const canarySegment = document.getElementById('canarySegment');
            canarySegment.classList.add('degraded');
        } else if (analysis.phase === 'Error') {
            aiIcon.textContent = '⚠️';
            aiStatusTitle.textContent = 'Analysis Error';
            aiStatusSubtitle.textContent = 'Error during analysis';
            aiDecision.classList.add('failed');
            aiDecisionTitle.textContent = 'Error';
            aiDecisionMessage.textContent = analysis.message || 'An error occurred during analysis.';
            
            if (analysis.errorLog) {
                errorLogContainer.style.display = 'block';
                document.getElementById('errorLogText').textContent = analysis.errorLog;
            } else {
                errorLogContainer.style.display = 'none';
            }
        } else {
            aiIcon.textContent = '📊';
            aiStatusTitle.textContent = 'Analysis: ' + analysis.phase;
            aiStatusSubtitle.textContent = 'Current status';
            aiDecisionTitle.textContent = analysis.phase;
            aiDecisionMessage.textContent = analysis.message || 'Analysis status: ' + analysis.phase;
            errorLogContainer.style.display = 'none';
        }
    } else {
        aiIcon.classList.remove('analyzing');
        aiIcon.textContent = '🤖';
        aiStatusTitle.textContent = 'AI Monitoring';
        aiStatusSubtitle.textContent = 'Waiting for analysis to start';
        aiDecision.classList.remove('success', 'failed');
        aiDecisionTitle.textContent = 'Waiting...';
        aiDecisionMessage.textContent = analysis && analysis.message ? analysis.message : 'The AI agent will analyze metrics once the rollout progresses and sufficient data is collected.';
        errorLogContainer.style.display = 'none';
    }
    
        const graphSuccessRate = canarySuccessRate > 0 ? canarySuccessRate : stableSuccessRate;
        updateSuccessRateGraph(graphSuccessRate);
    } catch (error) {
        console.error('Error in updateAIAnalysis:', error);
    }
}

function toggleErrorLog() {
    const content = document.getElementById('errorLogContent');
    const toggle = document.getElementById('errorLogToggle');
    
    if (content.classList.contains('expanded')) {
        content.classList.remove('expanded');
        toggle.classList.remove('expanded');
    } else {
        content.classList.add('expanded');
        toggle.classList.add('expanded');
    }
}

// Make toggleErrorLog available globally for onclick handler
window.toggleErrorLog = toggleErrorLog;

const successRateHistory = [];
const maxHistoryPoints = 30;

function updateSuccessRateGraph(currentSuccessRate) {
    successRateHistory.push(currentSuccessRate);
    if (successRateHistory.length > maxHistoryPoints) {
        successRateHistory.shift();
    }
    
    const canvas = document.getElementById('successRateGraph');
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;
    
    ctx.clearRect(0, 0, width, height);
    
    if (successRateHistory.length < 2) return;
    
    ctx.strokeStyle = '#e5e7eb';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
        const y = (height / 4) * i;
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
    }
    
    ctx.strokeStyle = currentSuccessRate >= 95 ? '#10b981' : currentSuccessRate >= 90 ? '#f59e0b' : '#ef4444';
    ctx.lineWidth = 2;
    ctx.beginPath();
    
    const pointSpacing = width / (maxHistoryPoints - 1);
    
    successRateHistory.forEach((rate, index) => {
        const x = index * pointSpacing;
        const y = height - (rate / 100) * height;
        
        if (index === 0) {
            ctx.moveTo(x, y);
        } else {
            ctx.lineTo(x, y);
        }
    });
    
    ctx.stroke();
    
    ctx.strokeStyle = '#94a3b8';
    ctx.lineWidth = 1;
    ctx.setLineDash([5, 5]);
    const thresholdY = height - (95 / 100) * height;
    ctx.beginPath();
    ctx.moveTo(0, thresholdY);
    ctx.lineTo(width, thresholdY);
    ctx.stroke();
    ctx.setLineDash([]);
}

function updateScenarios(scenariosData) {
    document.getElementById('stableScenario').textContent = scenariosData.stable || 'unknown';
    document.getElementById('canaryScenario').textContent = scenariosData.canary || 'none';
}

let previousStableRequests = 0;
let previousCanaryRequests = 0;

function animateRequest(type) {
    const container = document.getElementById('requestVisualization');
    if (!container) return;
    
    const dot = document.createElement('div');
    dot.className = 'request-dot ' + type;
    dot.style.left = Math.random() * (container.offsetWidth - 18) + 'px';
    dot.style.bottom = '0px';
    
    container.appendChild(dot);
    
    setTimeout(() => {
        if (dot.parentNode) {
            dot.parentNode.removeChild(dot);
        }
    }, 5000);
}

function visualizeRealRequests(versionMetrics) {
    const stableRequests = versionMetrics.stableRequestCount || 0;
    const canaryRequests = versionMetrics.canaryRequestCount || 0;
    const canarySuccessRate = versionMetrics.canarySuccessRate || 100;
    
    const stableDelta = Math.max(0, stableRequests - previousStableRequests);
    const canaryDelta = Math.max(0, canaryRequests - previousCanaryRequests);
    
    // Calculate success and error deltas based on success rate
    const canarySuccessDelta = Math.round((canarySuccessRate / 100) * canaryDelta);
    const canaryErrorDelta = canaryDelta - canarySuccessDelta;
    
    // Animate stable requests (blue dots)
    for (let i = 0; i < Math.min(stableDelta, 20); i++) {
        setTimeout(() => animateRequest('stable'), i * 50);
    }
    
    // Animate canary success requests (green dots)
    for (let i = 0; i < Math.min(canarySuccessDelta, 20); i++) {
        setTimeout(() => animateRequest('canary-success'), i * 50 + 10);
    }
    
    // Animate canary error requests (red dots)
    for (let i = 0; i < Math.min(canaryErrorDelta, 20); i++) {
        setTimeout(() => animateRequest('canary-error'), i * 50 + 20);
    }
    
    previousStableRequests = stableRequests;
    previousCanaryRequests = canaryRequests;
}

function showError(message) {
    const errorContainer = document.getElementById('errorContainer');
    errorContainer.innerHTML = '<div class="error-message">' +
        '<div class="error-title">Error</div>' +
        '<div>' + message + '</div>' +
        '</div>';
}

function clearError() {
    document.getElementById('errorContainer').innerHTML = '';
}

// Initialize dashboard when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        updateDashboard();
        setInterval(updateDashboard, 2000);
    });
} else {
    updateDashboard();
    setInterval(updateDashboard, 2000);
}
