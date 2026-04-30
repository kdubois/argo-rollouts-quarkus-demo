// AI-Powered Progressive Delivery Dashboard

let previousSuccessRate = null;
let previousErrorRate = null;

function updateDashboard() {
    Promise.all([
        fetch('/api/status').then(r => r.json()),
        fetch('/api/rollout/status').then(r => r.json()),
        fetch('/api/rollout/metrics').then(r => r.json())
    ])
    .then(([metricsData, rolloutData, versionMetrics]) => {
        updateRolloutProgress(rolloutData);
        updateTrafficDistribution(rolloutData);
        updateAIAnalysis(rolloutData, metricsData, versionMetrics);
        visualizeRealRequests(versionMetrics);
        clearError();
    })
    .catch(error => {
        console.error('Error updating dashboard:', error);
        const isConnectionError = error.message.includes('Failed to fetch') ||
                                 error.message.includes('NetworkError') ||
                                 error.message.includes('fetch');
        if (isConnectionError) {
            showError('Not connected to cluster. Running in demo mode with simulated data.');
            showDemoMode();
        } else {
            showError('Failed to fetch dashboard data: ' + error.message);
        }
    });
}

function showDemoMode() {
    document.getElementById('rolloutStatusBadge').innerHTML =
        '<div class="status-badge progressing"><div class="status-dot"></div>Demo Mode</div>';

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
        '<div class="status-dot"></div>' + phase + '</div>';

    const rolloutMessage = document.getElementById('rolloutMessage');
    if (rolloutMessage) {
        let message = rolloutData.message || '';
        if (!message) {
            if (phase === 'Healthy') {
                message = 'Rollout completed successfully';
            } else if (phase === 'Progressing') {
                message = 'Rollout in progress';
            } else if (phase === 'Paused') {
                message = 'Paused for AI analysis';
            } else if (phase === 'Degraded') {
                message = 'Issues detected - degraded';
            } else {
                message = 'Monitoring deployment';
            }
        }
        rolloutMessage.textContent = message;
    }

    // Update stage pips
    const pips = document.querySelectorAll('.stage-pip');
    pips.forEach(pip => {
        const weight = parseInt(pip.dataset.weight);
        const stepAttr = pip.dataset.step;
        pip.classList.remove('active', 'completed', 'paused');

        let isCurrentStage = false;
        let isPastStage = false;
        if (currentStepIndex !== null && currentStepIndex !== undefined && stepAttr) {
            const steps = stepAttr.split(',').map(s => parseInt(s.trim()));
            isCurrentStage = steps.includes(currentStepIndex);
            isPastStage = steps.every(s => s < currentStepIndex);
        }

        if (isPastStage || weight < canaryWeight) {
            pip.classList.add('completed');
        } else if (isCurrentStage || (weight === canaryWeight && (currentStepIndex === null || currentStepIndex === undefined))) {
            if (phase === 'Paused') {
                pip.classList.add('paused');
            } else {
                pip.classList.add('active');
            }
        }
    });
}

function updateTrafficDistribution(rolloutData) {
    const canaryWeight = rolloutData.canaryWeight ?? 0;
    const stableWeight = 100 - canaryWeight;

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
        const stableSuccessRate = Math.round(versionMetrics.stableSuccessRate || 0);
        const canarySuccessRate = Math.round(versionMetrics.canarySuccessRate || 0);

        // Update canary segment visual state based on analysis
        const analysis = rolloutData.analysis;
        const canarySegment = document.getElementById('canarySegment');
        
        if (analysis && (analysis.phase === 'Failed' || analysis.phase === 'Degraded' || analysis.successful === false)) {
            canarySegment.classList.add('degraded');
        } else {
            canarySegment.classList.remove('degraded');
        }

        const graphSuccessRate = (versionMetrics.canaryRequestCount > 0) ? canarySuccessRate : stableSuccessRate;
        updateSuccessRateGraph(graphSuccessRate);
    } catch (error) {
        console.error('Error in updateAIAnalysis:', error);
    }
}

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

    // Auto-scale Y-axis to make variations visible
    const dataMin = Math.min(...successRateHistory);
    const dataMax = Math.max(...successRateHistory);
    const dataRange = Math.max(dataMax - dataMin, 5);
    const yMin = Math.max(0, Math.floor(dataMin - dataRange * 0.3));
    const yMax = Math.min(100, Math.ceil(dataMax + dataRange * 0.2));
    const yRange = yMax - yMin;

    function rateToY(rate) {
        return height - ((rate - yMin) / yRange) * height;
    }

    // Grid lines with labels
    ctx.strokeStyle = 'rgba(100, 116, 139, 0.2)';
    ctx.fillStyle = 'rgba(100, 116, 139, 0.5)';
    ctx.font = '10px monospace';
    ctx.lineWidth = 1;
    const gridStep = yRange <= 10 ? 2 : yRange <= 25 ? 5 : 10;
    const gridStart = Math.ceil(yMin / gridStep) * gridStep;
    for (let v = gridStart; v <= yMax; v += gridStep) {
        const y = rateToY(v);
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
        ctx.fillText(v + '%', 4, y - 3);
    }

    // Data line
    ctx.strokeStyle = currentSuccessRate >= 95 ? '#10b981' : currentSuccessRate >= 90 ? '#f59e0b' : '#ef4444';
    ctx.lineWidth = 2;
    ctx.beginPath();

    const pointSpacing = width / (maxHistoryPoints - 1);
    successRateHistory.forEach((rate, index) => {
        const x = index * pointSpacing;
        const y = rateToY(rate);
        if (index === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // 95% threshold line (only if in visible range)
    if (95 >= yMin && 95 <= yMax) {
        ctx.strokeStyle = 'rgba(148, 163, 184, 0.4)';
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);
        const thresholdY = rateToY(95);
        ctx.beginPath();
        ctx.moveTo(0, thresholdY);
        ctx.lineTo(width, thresholdY);
        ctx.stroke();
        ctx.setLineDash([]);
    }
}

let previousStableRequests = 0;
let previousCanaryRequests = 0;

function animateRequest(type) {
    const container = document.getElementById('requestVisualization');
    if (!container) return;

    const dot = document.createElement('div');
    dot.className = 'request-dot ' + type;
    dot.style.left = Math.random() * (container.offsetWidth - 14) + 'px';
    dot.style.bottom = '0px';
    dot.style.setProperty('--travel-distance', '-' + container.offsetHeight + 'px');
    container.appendChild(dot);

    setTimeout(() => {
        if (dot.parentNode) dot.parentNode.removeChild(dot);
    }, 4000);
}

function visualizeRealRequests(versionMetrics) {
    const stableRequests = versionMetrics.stableRequestCount || 0;
    const canaryRequests = versionMetrics.canaryRequestCount || 0;
    const canarySuccessRate = versionMetrics.canarySuccessRate || 100;

    const stableDelta = Math.max(0, stableRequests - previousStableRequests);
    const canaryDelta = Math.max(0, canaryRequests - previousCanaryRequests);
    const canarySuccessDelta = Math.round((canarySuccessRate / 100) * canaryDelta);
    const canaryErrorDelta = canaryDelta - canarySuccessDelta;

    for (let i = 0; i < Math.min(stableDelta, 15); i++) {
        setTimeout(() => animateRequest('stable'), i * 60);
    }
    for (let i = 0; i < Math.min(canarySuccessDelta, 15); i++) {
        setTimeout(() => animateRequest('canary-success'), i * 60 + 10);
    }
    for (let i = 0; i < Math.min(canaryErrorDelta, 15); i++) {
        setTimeout(() => animateRequest('canary-error'), i * 60 + 20);
    }

    previousStableRequests = stableRequests;
    previousCanaryRequests = canaryRequests;
}

function showError(message) {
    document.getElementById('errorContainer').innerHTML =
        '<div class="error-message"><div class="error-title">Error</div><div>' + message + '</div></div>';
}

function clearError() {
    document.getElementById('errorContainer').innerHTML = '';
}

// === AGENT ACTIVITY FEED ===
let lastEventId = 0;
let activityPollInterval = null;

function appendActivityItem(event) {
    const feed = document.getElementById('activityFeed');
    const empty = document.getElementById('activityEmpty');
    if (empty) empty.style.display = 'none';

    const typeClass = event.type.toLowerCase().replace(/_/g, '-');
    const time = new Date(event.timestamp).toLocaleTimeString();

    const item = document.createElement('div');
    item.className = 'activity-item';

    if (event.type === 'DECISION') {
        item.classList.add('decision-event');
        if (event.message.includes('PROMOTE')) item.classList.add('promote');
        else item.classList.add('rollback');
    } else if (event.type === 'ERROR') {
        item.classList.add('error-event');
    } else if (event.type === 'REMEDIATION') {
        item.classList.add('remediation-event');
    } else if (event.type === 'ANALYSIS_INSIGHT') {
        item.classList.add('insight-event');
    } else if (event.type === 'CONFIDENCE_SCORE') {
        item.classList.add('score-event');
    } else if (event.type === 'RETRY') {
        item.classList.add('retry-event');
    } else if (event.type === 'ANALYSIS_SUMMARY') {
        item.classList.add('summary-event');
    }

    let messageHtml = escapeHtml(event.message);

    if (event.type === 'CONFIDENCE_SCORE') {
        const scoreMatch = event.message.match(/Score:\s*(\d+)/);
        if (scoreMatch) {
            const score = parseInt(scoreMatch[1]);
            const scoreClass = score >= 70 ? 'score-high' : score >= 50 ? 'score-mid' : 'score-low';
            messageHtml = 'Score: <span class="score-value ' + scoreClass + '">' + score + '</span>/100';
        }
    }

    let html = '<div class="activity-dot-indicator ' + typeClass + '"></div>' +
        '<div class="activity-body">' +
        '<div class="activity-item-header">' +
        '<span class="activity-type-badge ' + typeClass + '">' +
        formatEventType(event.type) + '</span>' +
        '<span class="activity-timestamp">' + time + '</span>' +
        '</div>' +
        '<div class="activity-message">' + messageHtml + '</div>';

    if (event.details) {
        html += '<div class="activity-details">' + escapeHtml(event.details) + '</div>';
    }

    html += '</div>';
    item.innerHTML = html;
    feed.appendChild(item);

    lastEventId = Math.max(lastEventId, event.id);
    feed.scrollTop = feed.scrollHeight;
}

function setActivityStatus(connected) {
    const status = document.getElementById('activityStatus');
    if (!status) return;
    if (connected) {
        status.classList.remove('polling');
        status.querySelector('span').textContent = 'Connected';
    } else {
        status.classList.add('polling');
        status.querySelector('span').textContent = 'Polling';
    }
}

function initActivityStream() {
    const evtSource = new EventSource('/api/agent/events/stream');

    evtSource.onopen = function() {
        setActivityStatus(true);
    };

    evtSource.onmessage = function(e) {
        try {
            const event = JSON.parse(e.data);
            appendActivityItem(event);
        } catch (err) {
            console.debug('Failed to parse SSE event:', err);
        }
    };

    evtSource.onerror = function() {
        evtSource.close();
        console.warn('SSE connection failed, falling back to polling');
        setActivityStatus(false);
        startActivityPolling();
    };
}

function startActivityPolling() {
    if (activityPollInterval) return;
    activityPollInterval = setInterval(pollActivityFeed, 3000);
}

function pollActivityFeed() {
    const url = lastEventId > 0
        ? '/api/agent/events?since=' + lastEventId
        : '/api/agent/events';

    fetch(url)
        .then(r => r.json())
        .then(events => {
            events.forEach(event => appendActivityItem(event));
        })
        .catch(err => {
            console.debug('Activity feed unavailable:', err.message);
        });
}

function loadActivityHistory() {
    fetch('/api/agent/events')
        .then(r => r.json())
        .then(events => {
            events.forEach(event => appendActivityItem(event));
        })
        .catch(() => {});
}

function formatEventType(type) {
    const labels = {
        'ANALYSIS_START': 'Start',
        'TOOL_CALL': 'Tool',
        'TOOL_RESULT': 'Result',
        'DECISION': 'Decision',
        'REMEDIATION': 'Fix',
        'ERROR': 'Error',
        'AGENT_START': 'Agent',
        'AGENT_COMPLETE': 'Done',
        'ANALYSIS_INSIGHT': 'Insight',
        'CONFIDENCE_SCORE': 'Score',
        'RETRY': 'Retry',
        'ANALYSIS_SUMMARY': 'Summary'
    };
    return labels[type] || type;
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;');
}

// Initialize
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

function init() {
    updateDashboard();
    setInterval(updateDashboard, 2000);
    loadActivityHistory();
    initActivityStream();
}
