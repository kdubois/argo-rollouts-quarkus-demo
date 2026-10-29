// AI-Powered Progressive Delivery Dashboard

let previousSuccessRate = null;
let previousErrorRate = null;
const DISPLAY_MODE_KEY = 'dashboardDisplayMode';

function getStoredDisplayMode() {
    try {
        return localStorage.getItem(DISPLAY_MODE_KEY);
    } catch {
        return null;
    }
}

function setStoredDisplayMode(mode) {
    try {
        localStorage.setItem(DISPLAY_MODE_KEY, mode);
    } catch {
        // Ignore storage restrictions and keep in-memory mode only.
    }
}

function getDisplayModeFromUrl() {
    try {
        const params = new URLSearchParams(window.location.search);
        const mode = params.get('mode');
        if (mode === 'projector' || mode === 'stage') {
            return mode;
        }
    } catch {
        // Ignore malformed URL state.
    }
    return null;
}

function applyDisplayMode(mode) {
    const body = document.body;
    if (!body) return;
    const isProjectorMode = mode === 'projector';
    body.classList.toggle('projector-mode', isProjectorMode);
    body.classList.toggle('stage-mode', !isProjectorMode);

    const toggle = document.getElementById('displayModeToggle');
    if (toggle) {
        toggle.textContent = isProjectorMode ? 'Dark Mode' : 'Light Mode';
        toggle.setAttribute('aria-pressed', isProjectorMode ? 'true' : 'false');
    }
}

function initDisplayModeControls() {
    const toggle = document.getElementById('displayModeToggle');
    if (!toggle) return;

    const initialMode = getDisplayModeFromUrl() || getStoredDisplayMode() || 'stage';
    applyDisplayMode(initialMode);

    toggle.addEventListener('click', () => {
        const currentMode = document.body.classList.contains('projector-mode') ? 'projector' : 'stage';
        const nextMode = currentMode === 'projector' ? 'stage' : 'projector';
        applyDisplayMode(nextMode);
        setStoredDisplayMode(nextMode);

    });
}

function updateDashboard() {
    Promise.all([
        fetch('/api/status').then(r => r.json()),
        fetch('/api/rollout/status').then(r => r.json()),
        fetch('/api/rollout/metrics').then(r => r.json())
    ])
    .then(([metricsData, rolloutData, versionMetrics]) => {
        updateRolloutProgress(rolloutData);
        updateTrafficDistribution(rolloutData, versionMetrics);
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

let lastRolloutPhase = null;

function updateRolloutProgress(rolloutData) {
    const phase = rolloutData.phase || 'Unknown';

    // a new rollout starting clears the remediation log so the previous
    // run's output is never confused with the current one
    if (phase === 'Progressing' && lastRolloutPhase && lastRolloutPhase !== 'Progressing') {
        revertRequestsBox();
    }
    lastRolloutPhase = phase;
    // a Degraded rollout has rolled back: the canary serves no traffic,
    // even while the status still reports the last step's weight
    const canaryWeight = phase === 'Degraded' ? 0 : (rolloutData.canaryWeight || 0);
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

function updateTrafficDistribution(rolloutData, versionMetrics) {
    const canaryWeight = rolloutData.phase === 'Degraded' ? 0 : (rolloutData.canaryWeight ?? 0);
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

    const stablePodCount = document.getElementById('stablePodCount');
    const canaryPodCount = document.getElementById('canaryPodCount');
    if (stablePodCount) {
        const count = versionMetrics?.stablePodCount ?? 0;
        stablePodCount.textContent = count + ' pod' + (count === 1 ? '' : 's');
    }
    if (canaryPodCount) {
        const count = versionMetrics?.canaryPodCount ?? 0;
        canaryPodCount.textContent = count + ' pod' + (count === 1 ? '' : 's');
    }
}

function updateAIAnalysis(rolloutData, metricsData, versionMetrics) {
    try {
        const stableSuccessRate = Math.round(versionMetrics.stableSuccessRate || 0);
        const canarySuccessRate = Math.round(versionMetrics.canarySuccessRate || 0);

        const analysis = rolloutData.analysis;
        const canarySegment = document.getElementById('canarySegment');

        if (analysis && (analysis.phase === 'Failed' || analysis.phase === 'Degraded' || analysis.successful === false)) {
            canarySegment.classList.add('degraded');
        } else {
            canarySegment.classList.remove('degraded');
        }

        updateDecisionCard(analysis, rolloutData.phase);

    } catch (error) {
        console.error('Error in updateAIAnalysis:', error);
    }
}

function updateDecisionCard(analysis, rolloutPhase) {
    const card = document.getElementById('decisionCard');
    const phaseBadge = document.getElementById('decisionPhaseBadge');
    const verdictIcon = document.getElementById('verdictIcon');
    const verdictText = document.getElementById('verdictText');
    const messageEl = document.getElementById('decisionMessage');
    const errorDetails = document.getElementById('decisionErrorDetails');
    const errorLog = document.getElementById('decisionErrorLog');

    card.classList.remove('promote', 'rollback', 'running', 'pending', 'error');

    if (!analysis || analysis.phase === 'NotStarted') {
        phaseBadge.textContent = 'Pending';
        card.classList.add('pending');
        verdictIcon.textContent = '\u2014';
        verdictText.textContent = 'Waiting for analysis to begin';
        messageEl.textContent = '';
        errorDetails.style.display = 'none';
        return;
    }

    const measurement = (analysis.currentMeasurement && analysis.totalMeasurements)
        ? '  \u00b7  Measurement ' + analysis.currentMeasurement + ' of ' + analysis.totalMeasurements
        : '';
    phaseBadge.textContent = (analysis.phase || 'Unknown') + measurement;

    if (analysis.phase === 'Running') {
        card.classList.add('running');
        verdictIcon.textContent = '\u25B6';
        verdictText.textContent = 'Analysis in progress' + (measurement ? ' (' + analysis.currentMeasurement + '/' + analysis.totalMeasurements + ')' : '') + '...';
    } else if (analysis.successful === true) {
        card.classList.add('promote');
        verdictIcon.textContent = '\u2714';
        verdictText.textContent = 'Promote recommended';
    } else if (analysis.successful === false) {
        card.classList.add('rollback');
        verdictIcon.textContent = '\u2718';
        verdictText.textContent = 'Rollback recommended';
    } else if (analysis.phase === 'Error') {
        card.classList.add('error');
        verdictIcon.textContent = '\u26A0';
        verdictText.textContent = 'Analysis error';
    } else {
        card.classList.add('pending');
        verdictIcon.textContent = '\u2014';
        verdictText.textContent = analysis.phase;
    }

    if (rolloutPhase === 'Healthy') {
        card.classList.remove('running', 'pending');
        card.classList.add('promote');
        verdictIcon.textContent = '\u2714';
        verdictText.textContent = 'Rollout completed \u2014 promoted';
        phaseBadge.textContent = 'Successful';
        messageEl.textContent = analysis.message && analysis.message !== 'Analysis has not started yet'
            ? analysis.message : 'Canary passed AI analysis';
    } else if (rolloutPhase === 'Degraded' || rolloutPhase === 'Aborted') {
        card.classList.remove('running', 'pending', 'promote');
        card.classList.add('rollback');
        verdictIcon.textContent = '\u2718';
        verdictText.textContent = 'Rollout aborted \u2014 rolled back';
        phaseBadge.textContent = 'Failed';
        messageEl.textContent = analysis.message && analysis.message !== 'Analysis has not started yet'
            ? analysis.message : 'AI analysis detected issues — automatic rollback applied';
    } else {
        messageEl.textContent = analysis.message || '';
    }

    if (analysis.errorLog) {
        errorDetails.style.display = 'block';
        errorLog.textContent = analysis.errorLog;
    } else {
        errorDetails.style.display = 'none';
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
    if (event.id <= lastEventId && event.id > 0) {
        if (event.id < lastEventId / 2) {
            resetActivityFeed();
        } else {
            return;
        }
    }

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
        // surface the PR link in the remediation log box as its final line
        if (sandboxName && event.details && event.details.startsWith('http')) {
            appendStreamLine({ type: 'result', text: 'PR: ' + event.details });
        }
    } else if (event.type === 'ANALYSIS_INSIGHT') {
        item.classList.add('insight-event');
    } else if (event.type === 'CONFIDENCE_SCORE') {
        item.classList.add('score-event');
    } else if (event.type === 'RETRY') {
        item.classList.add('retry-event');
    } else if (event.type === 'ANALYSIS_SUMMARY') {
        item.classList.add('summary-event');
    } else if (event.type === 'PAUSE') {
        item.classList.add('pause-event');
    } else if (event.type === 'PAUSE_FAILED') {
        item.classList.add('pause-failed-event');
    }

    let messageHtml = (event.type === 'REMEDIATION')
        ? linkifyDetails(event.message)
        : escapeHtml(event.message);

    if (event.type === 'CONFIDENCE_SCORE') {
        const scoreMatch = event.message.match(/Score:\s*(\d+)/);
        if (scoreMatch) {
            const score = parseInt(scoreMatch[1]);
            const scoreClass = score >= 70 ? 'score-high' : score >= 50 ? 'score-mid' : 'score-low';
            messageHtml = 'Score: <span class="score-value ' + scoreClass + '">' + score + '</span>/100';
        }
    }

    let html = '';

    if (event.type === 'DECISION') {
        const isPromote = event.message.includes('PROMOTE');
        const verdictIcon = isPromote ? '\u2714' : '\u2718';
        const verdictLabel = isPromote ? 'Promote' : 'Rollback';
        html = '<div class="activity-dot-indicator ' + typeClass + '"></div>' +
            '<div class="activity-body">' +
            '<div class="activity-item-header">' +
            '<span class="activity-type-badge ' + typeClass + '">' +
            formatEventType(event.type) + '</span>' +
            '<span class="activity-timestamp">' + time + '</span>' +
            '</div>' +
            '<div class="decision-event-verdict">' +
            '<span class="decision-event-icon">' + verdictIcon + '</span>' +
            '<span class="decision-event-label">' + verdictLabel + '</span>' +
            '</div>' +
            '<div class="activity-message">' + messageHtml + '</div>';
    } else {
        html = '<div class="activity-dot-indicator ' + typeClass + '"></div>' +
            '<div class="activity-body">' +
            '<div class="activity-item-header">' +
            '<span class="activity-type-badge ' + typeClass + '">' +
            formatEventType(event.type) + '</span>' +
            '<span class="activity-timestamp">' + time + '</span>' +
            '</div>' +
            '<div class="activity-message">' + messageHtml + '</div>';
    }

    if (event.details) {
        html += '<div class="activity-details">' + linkifyDetails(event.details) + '</div>';
    }

    html += '</div>';
    item.innerHTML = html;
    feed.appendChild(item);

    lastEventId = Math.max(lastEventId, event.id);
    feed.scrollTop = feed.scrollHeight;
}

function resetActivityFeed() {
    const feed = document.getElementById('activityFeed');
    feed.innerHTML = '<div class="activity-empty" id="activityEmpty" style="display:none"></div>';
    lastEventId = 0;
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

function linkifyDetails(str) {
    if (!str) return '';
    var urlRe = /(https?:\/\/[^\s<>"']+)/;
    return str.split(urlRe).map(function(part, i) {
        if (i % 2 === 1) {
            var escaped = escapeHtml(part);
            return '<a href="' + escaped + '" target="_blank" rel="noopener" class="activity-link">' + escaped + '</a>';
        }
        return escapeHtml(part);
    }).join('');
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
    initDisplayModeControls();
    updateDashboard();
    setInterval(updateDashboard, 2000);
    loadActivityHistory();
    initActivityStream();
    initSandboxStream();
}

// === AI FIX STREAM ===
// While a remediation sandbox runs, the live-requests box shows the coding
// agent's activity instead. The requests themselves keep flowing — only the
// visualization swaps.
let sandboxName = null;
let sandboxNextLine = 0;
let sandboxDone = false;
let sandboxRevertTimer = null;
let streamTimerInterval = null;
let streamStartedAt = null;

function startStreamTimer() {
    streamStartedAt = Date.now();
    stopStreamTimer();
    const card = document.getElementById('requestsCard');
    if (card) card.classList.add('remediating');
    streamTimerInterval = setInterval(() => {
        const el = document.getElementById('streamTimer');
        if (!el || !streamStartedAt) return;
        const s = Math.floor((Date.now() - streamStartedAt) / 1000);
        el.textContent = '· ' + Math.floor(s / 60) + 'm ' + String(s % 60).padStart(2, '0') + 's';
    }, 1000);
}

function stopStreamTimer() {
    if (streamTimerInterval) clearInterval(streamTimerInterval);
    streamTimerInterval = null;
    const el = document.getElementById('streamTimer');
    if (el) el.textContent = '';
}

function initSandboxStream() {
    setInterval(pollSandbox, 2000);
}

function pollSandbox() {
    fetch('/api/sandbox/active')
        .then(r => {
            if (!r.ok) throw new Error('none');
            return r.json();
        })
        .then(active => {
            if (active.name && active.name !== sandboxName) {
                startSandboxStream(active.name);
            } else if (active.name && !sandboxDone) {
                fetchSandboxLog();
            }
        })
        .catch(() => {
            // no active sandbox: leave the finished log visible until the
            // next rollout starts (phase transition clears it)
        });
}

function startSandboxStream(name) {
    sandboxName = name;
    sandboxNextLine = 0;
    sandboxDone = false;
    clearTimeout(sandboxRevertTimer);
    sandboxRevertTimer = null;

    const box = document.getElementById('requestVisualization');
    const stream = document.getElementById('agentStream');
    const title = document.getElementById('requestsTitle');
    const legend = document.getElementById('requestLegend');
    if (box) box.style.display = 'none';
    if (legend) legend.style.display = 'none';
    if (stream) {
        stream.innerHTML = '';
        stream.style.display = 'flex';
    }
    if (title) title.firstChild.textContent = 'Remediation Log ';
    startStreamTimer();
    fetchSandboxLog();
}

function fetchSandboxLog() {
    if (!sandboxName) return;
    fetch('/api/sandbox/' + sandboxName + '/log?after=' + sandboxNextLine)
        .then(r => r.json())
        .then(data => {
            (data.lines || []).forEach(line => appendStreamLine(line));
            sandboxNextLine = data.next || sandboxNextLine;
            if (data.done) {
                sandboxDone = true;
                const title = document.getElementById('requestsTitle');
                stopStreamTimer();
                if (title) title.firstChild.textContent = 'Remediation Log ';
            }
        })
        .catch(err => console.debug('Sandbox log unavailable:', err.message));
}

function appendStreamLine(line) {
    const stream = document.getElementById('agentStream');
    if (!stream) return;
    const icons = { tool: '⚙', agent: '💬', wrapper: '›', result: '✔' };
    const div = document.createElement('div');
    div.className = 'stream-line ' + line.type;
    const icon = document.createElement('span');
    icon.className = 'stream-icon';
    icon.textContent = icons[line.type] || '·';
    const text = document.createElement('span');
    text.className = 'stream-text';
    text.textContent = line.text;
    div.appendChild(icon);
    div.appendChild(text);
    stream.appendChild(div);
    while (stream.children.length > 40) {
        stream.removeChild(stream.firstChild);
    }
    stream.scrollTop = stream.scrollHeight;
}

function revertRequestsBox() {
    sandboxName = null;
    sandboxNextLine = 0;
    sandboxDone = false;
    sandboxRevertTimer = null;
    stopStreamTimer();
    streamStartedAt = null;
    const card = document.getElementById('requestsCard');
    if (card) card.classList.remove('remediating');
    const box = document.getElementById('requestVisualization');
    const stream = document.getElementById('agentStream');
    const title = document.getElementById('requestsTitle');
    const legend = document.getElementById('requestLegend');
    if (stream) {
        stream.style.display = 'none';
        stream.innerHTML = '';
    }
    if (box) box.style.display = 'block';
    if (legend) legend.style.display = 'flex';
    if (title) title.firstChild.textContent = 'Live Requests ';
}
